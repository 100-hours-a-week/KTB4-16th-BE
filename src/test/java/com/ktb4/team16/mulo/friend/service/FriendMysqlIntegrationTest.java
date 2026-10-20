package com.ktb4.team16.mulo.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.friend.dto.response.FriendListData;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 실제 MySQL 제약과 행 잠금 아래 친구 상태 전이 및 keyset 조회를 검증한다. */
@SpringBootTest
class FriendMysqlIntegrationTest {

    @Autowired
    private FriendCommandService commandService;

    @Autowired
    private FriendQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 예약 실행만 막고 친구 명령과 커밋 직후 Outbox 처리는 실제 빈으로 검증한다.
    @MockitoBean
    private NotificationOutboxScheduler scheduler;

    private final List<Long> createdUserIds = new ArrayList<>();

    /** 테스트가 남긴 관계와 사용자만 외래 키 순서에 맞춰 정리한다. */
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("""
                    DELETE FROM outbox_events
                    WHERE event_type IN ('FRIEND_REQUEST', 'FRIEND_ACCEPTED')
                        AND JSON_CONTAINS(payload, JSON_ARRAY(?), '$.recipientUserIds')
                    """, userId);
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM friend_requests WHERE requester_id = ? OR addressee_id = ?",
                    userId, userId);
            jdbcTemplate.update("DELETE FROM friendships WHERE user_low_id = ? OR user_high_id = ?",
                    userId, userId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    /** 역방향 요청 30회에서 친구 상태와 두 Outbox 완료·알림 수신자를 검증한다. */
    @RepeatedTest(30)
    void concurrentOppositeRequestsBecomeOneFriendship() throws Exception {
        TestUser first = insertUser("a");
        TestUser second = insertUser("b");

        List<Object> outcomes = runTogether(
                () -> commandService.sendFriendRequest(first.id(), second.nickname()),
                () -> commandService.sendFriendRequest(second.id(), first.nickname()));

        // 동시 실행의 완료 순서는 정해져 있지 않으므로 결과 종류만 순서 없이 검증한다.
        assertThat(outcomes).extracting(Object::getClass).containsExactlyInAnyOrder(
                SendFriendRequestResult.Pending.class,
                SendFriendRequestResult.BecameFriends.class);
        assertThat(requestCount(first.id(), second.id())).isZero();
        assertThat(friendshipCount(first.id(), second.id())).isEqualTo(1);

        Long requestId = outcomes.stream()
                .filter(SendFriendRequestResult.Pending.class::isInstance)
                .map(SendFriendRequestResult.Pending.class::cast)
                .findFirst().orElseThrow().friendRequestId();
        Long friendshipId = outcomes.stream()
                .filter(SendFriendRequestResult.BecameFriends.class::isInstance)
                .map(SendFriendRequestResult.BecameFriends.class::cast)
                .findFirst().orElseThrow().friendshipId();
        Map<String, Object> requestEvent = outboxEvent("FRIEND_REQUEST", requestId);
        Map<String, Object> acceptedEvent = outboxEvent("FRIEND_ACCEPTED", friendshipId);
        for (Map<String, Object> event : List.of(requestEvent, acceptedEvent)) {
            assertThat(event)
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("retry_count", 0)
                    .containsEntry("last_error_code", null);
        }

        // 요청 알림은 수락이 먼저 커밋되면 생략되지만, 수락 알림은 양쪽에 하나씩 필요하다.
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE source_event_id = ? AND type = 'FRIEND_REQUEST'
                """, Integer.class, requestEvent.get("event_id"))).isBetween(0, 1);
        assertThat(jdbcTemplate.queryForList("""
                SELECT user_id FROM notifications
                WHERE source_event_id = ? AND type = 'FRIEND_ACCEPTED'
                """, Long.class, acceptedEvent.get("event_id")))
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    /** 동일 방향의 동시 요청은 pending 하나만 남기고 중복 요청을 거절한다. */
    @Test
    void concurrentSameDirectionRequestsKeepOnePending() throws Exception {
        TestUser first = insertUser("a");
        TestUser second = insertUser("b");

        List<Object> outcomes = runTogether(
                () -> commandService.sendFriendRequest(first.id(), second.nickname()),
                () -> commandService.sendFriendRequest(first.id(), second.nickname()));

        // 동일 방향 요청도 어느 스레드가 먼저 성공할지는 보장되지 않는다.
        assertThat(outcomes).extracting(Object::getClass).containsExactlyInAnyOrder(
                SendFriendRequestResult.Pending.class,
                FriendDomainException.class);
        FriendDomainException conflict = (FriendDomainException) outcomes.stream()
                .filter(FriendDomainException.class::isInstance).findFirst().orElseThrow();
        assertThat(conflict.errorCode()).isEqualTo(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        assertThat(requestCount(first.id(), second.id())).isEqualTo(1);
        assertThat(friendshipCount(first.id(), second.id())).isZero();
    }

    /** generated low/high 열과 UNIQUE 제약이 역방향 중복 INSERT를 막는지 확인한다. */
    @Test
    void mysqlGeneratedPairRejectsOppositeDuplicateRequest() {
        TestUser first = insertUser("a");
        TestUser second = insertUser("b");
        commandService.sendFriendRequest(first.id(), second.nickname());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO friend_requests (requester_id, addressee_id) VALUES (?, ?)",
                second.id(), first.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(requestCount(first.id(), second.id())).isEqualTo(1);
    }

    /** 수락과 요청 취소가 경쟁해도 pending과 친구 관계가 동시에 남지 않는지 확인한다. */
    @Test
    void concurrentAcceptAndDeleteLeaveConsistentState() throws Exception {
        TestUser first = insertUser("a");
        TestUser second = insertUser("b");
        Long requestId = ((SendFriendRequestResult.Pending) commandService
                .sendFriendRequest(first.id(), second.nickname())).friendRequestId();

        List<Object> outcomes = runTogether(
                () -> commandService.acceptFriendRequest(second.id(), requestId),
                () -> {
                    commandService.deleteFriendRequest(first.id(), requestId);
                    return "deleted";
                });

        assertThat(outcomes.stream().filter(FriendDomainException.class::isInstance).count())
                .isEqualTo(1);
        assertThat(requestCount(first.id(), second.id())).isZero();
        assertThat(friendshipCount(first.id(), second.id())).isBetween(0, 1);
    }

    /** 실제 DB 정렬식과 cursor 경계가 모든 친구를 중복 없이 반환하는지 확인한다. */
    @Test
    void friendCursorTraversesMysqlRowsInNicknameOrder() {
        TestUser owner = insertUser("z");
        TestUser third = insertUser("c");
        TestUser first = insertUser("a");
        TestUser second = insertUser("b");
        for (TestUser friend : List.of(third, first, second)) {
            commandService.sendFriendRequest(owner.id(), friend.nickname());
            commandService.sendFriendRequest(friend.id(), owner.nickname());
        }

        FriendListData page1 = queryService.getFriends(owner.id(), null, 1);
        FriendListData page2 = queryService.getFriends(owner.id(), page1.nextCursor(), 1);
        FriendListData page3 = queryService.getFriends(owner.id(), page2.nextCursor(), 1);

        assertThat(List.of(page1.friends().getFirst().userId(),
                page2.friends().getFirst().userId(), page3.friends().getFirst().userId()))
                .containsExactly(first.id(), second.id(), third.id());
        assertThat(page3.nextCursor()).isNull();
    }

    /** 고유한 활성 사용자 한 명을 생성하고 정리 대상 ID를 기록한다. */
    private TestUser insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nickname = prefix + suffix;
        String email = nickname + "@example.test";
        jdbcTemplate.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, email);
        createdUserIds.add(userId);
        return new TestUser(userId, nickname);
    }

    /** 두 작업자가 모두 대기한 뒤 같은 시작 신호로 독립 트랜잭션을 실행한다. */
    private List<Object> runTogether(CheckedCommand first, CheckedCommand second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> firstFuture = executor.submit(() -> runAfterSignal(ready, start, first));
            Future<Object> secondFuture = executor.submit(() -> runAfterSignal(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstFuture.get(15, TimeUnit.SECONDS),
                    secondFuture.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /** 준비 상태를 알리고 동시에 시작하며 도메인 거절은 검증용 결과로 돌린다. */
    private Object runAfterSignal(CountDownLatch ready, CountDownLatch start,
            CheckedCommand command) throws Exception {
        ready.countDown();
        start.await();
        try {
            return command.execute();
        } catch (FriendDomainException exception) {
            return exception;
        }
    }

    /** 특정 사용자 쌍의 pending 요청 수를 DB에서 직접 확인한다. */
    private int requestCount(Long firstId, Long secondId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM friend_requests
                WHERE user_low_id = LEAST(?, ?) AND user_high_id = GREATEST(?, ?)
                """, Integer.class, firstId, secondId, firstId, secondId);
    }

    /** 특정 사용자 쌍의 친구 관계 수를 DB에서 직접 확인한다. */
    private int friendshipCount(Long firstId, Long secondId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM friendships
                WHERE user_low_id = LEAST(?, ?) AND user_high_id = GREATEST(?, ?)
                """, Integer.class, firstId, secondId, firstId, secondId);
    }

    /** 원본 요청·친구 관계 ID로 생성된 Outbox 사건의 저장·처리 상태를 조회한다. */
    private Map<String, Object> outboxEvent(String eventType, Long aggregateId) {
        return jdbcTemplate.queryForMap("""
                SELECT event_id, status, retry_count, last_error_code
                FROM outbox_events
                WHERE event_type = ? AND aggregate_id = ?
                """, eventType, aggregateId);
    }

    /** 테스트 명령이 반환하는 결과를 공통 형태로 받는다. */
    @FunctionalInterface
    private interface CheckedCommand {
        Object execute() throws Exception;
    }

    /** 생성한 사용자의 ID와 요청용 닉네임을 묶는다. */
    private record TestUser(Long id, String nickname) {
    }
}
