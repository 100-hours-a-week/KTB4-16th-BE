package com.ktb4.team16.mulo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.NicknameConflictException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class UserNicknameOutboxMysqlIntegrationTest {
    @Autowired private UserProfileService profileService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private ChatRoomClient chatRoomClient;

    private Long userId;

    /** 닉네임과 버전, 발행할 JSON이 함께 커밋되고 기존 HTTP 전달도 유지되는지 검증한다. */
    @Test
    void nicknameChangeCommitsVersionAndPendingSnapshot() {
        createUser();
        String nickname = uniqueNickname();

        profileService.updateNickname(userId, nickname);

        assertThat(jdbcTemplate.queryForList(
                "SELECT nickname, nickname_version FROM users WHERE user_id = ?", userId))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("nickname")).isEqualTo(nickname);
                    assertThat(row.get("nickname_version")).isEqualTo(1L);
                });
        assertThat(jdbcTemplate.queryForList("""
                SELECT event_id, nickname_version, payload, status, attempt_count
                FROM chat_nickname_outbox WHERE user_id = ?
                """, userId)).singleElement().satisfies(row -> {
                    assertThat(row.get("status")).isEqualTo("PENDING");
                    assertThat(row.get("attempt_count")).isEqualTo(0);
                    assertThat(row.get("nickname_version")).isEqualTo(1L);
                    JsonNode payload = objectMapper.readTree((String) row.get("payload"));
                    assertThat(payload.get("eventId").asString()).isEqualTo(row.get("event_id"));
                    assertThat(UUID.fromString(payload.get("eventId").asString())).isNotNull();
                    assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
                    assertThat(payload.get("eventType").asString()).isEqualTo("USER_NICKNAME_CHANGED");
                    assertThat(payload.get("userId").asLong()).isEqualTo(userId);
                    assertThat(payload.get("nickname").asString()).isEqualTo(nickname);
                    assertThat(payload.get("nicknameVersion").asLong()).isEqualTo(1L);
                    assertThat(OffsetDateTime.parse(payload.get("occurredAt").asString()).getOffset())
                            .isEqualTo(ZoneOffset.ofHours(9));
                });
        verify(chatRoomClient).updateParticipantNickname(userId, nickname);
    }

    /** Outbox의 버전 제약 위반으로 이미 flush한 사용자 갱신도 롤백되는지 검증한다. */
    @Test
    void outboxVersionConflictRollsBackNicknameAndSkipsHttp() {
        User user = createUser();
        jdbcTemplate.update("""
                INSERT INTO chat_nickname_outbox (event_id, user_id, nickname_version, payload)
                VALUES (?, ?, 1, JSON_OBJECT())
                """, UUID.randomUUID().toString(), userId);

        assertThatThrownBy(() -> profileService.updateNickname(userId, uniqueNickname()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_chat_nickname_outbox_user_version");

        assertThat(userRepository.findById(userId)).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getNickname()).isEqualTo(user.getNickname());
            assertThat(reloaded.getNicknameVersion()).isZero();
        });
        assertThat(outboxVersions()).containsExactly(1L);
        verifyNoInteractions(chatRoomClient);
    }

    /** 상위 트랜잭션 롤백 시 성공한 Outbox INSERT도 사용자 갱신과 함께 취소된다. */
    @Test
    void outerRollbackDiscardsAlreadyInsertedOutboxEvent() {
        User user = createUser();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            profileService.updateNickname(userId, uniqueNickname());
            assertThat(outboxVersions()).containsExactly(1L);
            status.setRollbackOnly();
        });

        assertThat(outboxVersions()).isEmpty();
        assertThat(userRepository.findById(userId)).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.getNickname()).isEqualTo(user.getNickname());
            assertThat(reloaded.getNicknameVersion()).isZero();
        });
        verifyNoInteractions(chatRoomClient);
    }

    /** 같은 이름과 다른 사용자의 이름은 기존 오류로 거절하고 DB에 변경을 남기지 않는다. */
    @Test
    void rejectedNicknamesLeaveVersionAndOutboxUntouched() {
        User user = createUser();
        User other = userRepository.saveAndFlush(User.signup(
                UUID.randomUUID() + "@example.test", "password-hash", uniqueNickname()));
        try {
            assertThatThrownBy(() -> profileService.updateNickname(userId, user.getNickname()))
                    .isInstanceOfSatisfying(NicknameConflictException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.SAME_NICKNAME));
            assertThatThrownBy(() -> profileService.updateNickname(userId, other.getNickname()))
                    .isInstanceOfSatisfying(NicknameConflictException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
            assertThat(userRepository.findById(userId)).hasValueSatisfying(reloaded -> {
                assertThat(reloaded.getNickname()).isEqualTo(user.getNickname());
                assertThat(reloaded.getNicknameVersion()).isZero();
            });
            assertThat(outboxVersions()).isEmpty();
            verifyNoInteractions(chatRoomClient);
        } finally {
            userRepository.deleteById(other.getUserId());
        }
    }

    /** 같은 사용자의 동시 닉네임 변경에 서로 다른 버전과 각각의 스냅샷을 기록한다. */
    @Test
    void concurrentNicknameChangesRecordSuccessiveVersions() throws Exception {
        createUser();
        String firstNickname = uniqueNickname();
        String secondNickname = uniqueNickname();

        runInCompetingTransactions(
                () -> profileService.updateNickname(userId, firstNickname),
                () -> profileService.updateNickname(userId, secondNickname));

        assertThat(outboxVersions()).containsExactly(1L, 2L);
        assertThat(jdbcTemplate.queryForList("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.nickname'))
                FROM chat_nickname_outbox WHERE user_id = ? ORDER BY nickname_version
                """, String.class, userId)).containsExactly(firstNickname, secondNickname);
        assertThat(userRepository.findById(userId)).hasValueSatisfying(user -> {
            assertThat(user.getNickname()).isEqualTo(secondNickname);
            assertThat(user.getNicknameVersion()).isEqualTo(2L);
        });
    }

    /** 어느 요청이 행 잠금을 먼저 얻어도 닉네임과 선호 장르를 모두 보존한다. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nicknameAndGenresDoNotOverwriteEachOther(boolean nicknameFirst) throws Exception {
        createUser();
        String nickname = uniqueNickname();
        List<String> genres = List.of("재즈");
        Runnable updateNickname = () -> profileService.updateNickname(userId, nickname);
        Runnable updateGenres = () -> profileService.updatePreferredGenres(userId, genres);

        runInCompetingTransactions(
                nicknameFirst ? updateNickname : updateGenres,
                nicknameFirst ? updateGenres : updateNickname);

        assertThat(userRepository.findById(userId)).hasValueSatisfying(user -> {
            assertThat(user.getNickname()).isEqualTo(nickname);
            assertThat(user.getNicknameVersion()).isEqualTo(1L);
            assertThat(user.getPreferredGenres()).containsExactlyElementsOf(genres);
        });
        assertThat(outboxVersions()).containsExactly(1L);
    }

    /** 첫 요청의 커밋을 보류한 상태에서 둘째 요청이 사용자 행의 잠금을 기다리게 한다. */
    private void runInCompetingTransactions(Runnable first, Runnable second) throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CompletableFuture<Void> secondReturned = new CompletableFuture<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var secondCommit = transactions.execute(status -> {
                first.run();
                var pending = executor.submit(() -> transactions.executeWithoutResult(secondStatus -> {
                    secondStarted.countDown();
                    second.run();
                    // 장르 변경이 잠금 없이 끝났다면 커밋에서만 대기하는 경우도 잡아낸다.
                    secondReturned.complete(null);
                }));
                assertThat(awaitStart(secondStarted)).isTrue();
                assertThatThrownBy(() -> secondReturned.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                return pending;
            });
            secondCommit.get(10, TimeUnit.SECONDS);
        }
    }

    /** 둘째 트랜잭션이 열릴 때까지만 기다리고, 인터럽트는 테스트 실패로 전달한다. */
    private boolean awaitStart(CountDownLatch started) {
        try {
            return started.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("둘째 트랜잭션 대기 중 인터럽트", exception);
        }
    }

    /** 해당 사용자의 이벤트 버전을 발생 순서대로 읽는다. */
    private List<Long> outboxVersions() {
        return jdbcTemplate.queryForList("""
                SELECT nickname_version FROM chat_nickname_outbox
                WHERE user_id = ? ORDER BY nickname_version
                """, Long.class, userId);
    }

    /** 테스트마다 독립적인 활성 사용자를 커밋한다. */
    private User createUser() {
        User user = userRepository.saveAndFlush(User.signup(
                UUID.randomUUID() + "@example.test", "password-hash", uniqueNickname()));
        userId = user.getUserId();
        return user;
    }

    /** 닉네임 길이 제한을 지키는 테스트 값을 만든다. */
    private String uniqueNickname() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** 이번 테스트가 직접 커밋한 데이터만 삭제한다. */
    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM chat_nickname_outbox WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
    }
}
