package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestCreateResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("integration-test")
class MatchConcurrencyMysqlIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchService matchService;

    @MockitoBean
    private Storage storage;

    @MockitoBean
    private Clock clock;

    private Long voteQuestionId;
    private Long waitingUserId;
    private Long requesterBUserId;
    private Long requesterCUserId;
    private Long waitingAnswerId;
    private Long requesterBAnswerId;
    private Long requesterCAnswerId;

    @BeforeEach
    void verifiesIsolatedDatabaseAndAppliedMigrations() throws Exception {
        when(clock.instant()).thenReturn(LocalDate.now(KST).atTime(12, 0).atZone(KST).toInstant());
        when(clock.getZone()).thenReturn(KST);
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
    }

    @AfterEach
    void removesOnlyRowsCreatedByThisTest() {
        if (voteQuestionId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM match_requests WHERE vote_answer_id IN "
                + "(SELECT vote_answer_id FROM vote_answers WHERE vote_question_id = ?)", voteQuestionId);
        jdbcTemplate.update("DELETE FROM matches WHERE vote_question_id = ?", voteQuestionId);
        jdbcTemplate.update("DELETE FROM vote_answers WHERE vote_question_id = ?", voteQuestionId);
        jdbcTemplate.update("DELETE FROM vote_questions WHERE vote_question_id = ?", voteQuestionId);
        jdbcTemplate.update("DELETE FROM users WHERE user_id IN (?, ?, ?)",
                waitingUserId, requesterBUserId, requesterCUserId);
    }

    @RepeatedTest(10)
    void concurrentRequestsDoNotMatchTheSameWaitingCandidateTwice() throws Exception {
        createFixture();

        List<MatchRequestCreateResponse> responses = runConcurrently();

        assertThat(responses).hasSize(2);

        List<MatchRequestState> requests = findRequestsForQuestion();
        MatchRequestState waiting = findByUserId(requests, waitingUserId);
        MatchRequestState requesterB = findByUserId(requests, requesterBUserId);
        MatchRequestState requesterC = findByUserId(requests, requesterCUserId);

        assertThat(waiting.status()).isEqualTo(MatchRequestStatus.MATCHING.name());
        assertThat(waiting.matchId()).isNotNull();
        List<MatchRequestState> contenders = List.of(requesterB, requesterC);
        List<MatchRequestState> winners = contenders.stream()
                .filter(request -> MatchRequestStatus.MATCHING.name().equals(request.status()))
                .toList();
        List<MatchRequestState> stillWaiting = contenders.stream()
                .filter(request -> MatchRequestStatus.WAITING.name().equals(request.status()))
                .toList();

        assertThat(winners).hasSize(1);
        assertThat(stillWaiting).hasSize(1);
        assertThat(winners.getFirst().matchId()).isEqualTo(waiting.matchId());
        assertThat(stillWaiting.getFirst().matchId()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE vote_question_id = ?", Integer.class,
                voteQuestionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_requests WHERE match_id = ?", Integer.class,
                waiting.matchId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT match_id) FROM match_requests "
                        + "WHERE vote_answer_id = ? AND match_id IS NOT NULL", Integer.class,
                waitingAnswerId)).isEqualTo(1);
        assertThat(responses).extracting(response -> response.data().status())
                .containsExactlyInAnyOrder(MatchRequestStatus.MATCHING, MatchRequestStatus.WAITING);
    }

    private List<MatchRequestCreateResponse> runConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MatchRequestCreateResponse>> futures = List.of(
                    executor.submit(() -> invokeAfterSignal(ready, start,
                            requesterBUserId, requesterBAnswerId)),
                    executor.submit(() -> invokeAfterSignal(ready, start,
                            requesterCUserId, requesterCAnswerId)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<MatchRequestCreateResponse> responses = new ArrayList<>();
            for (Future<MatchRequestCreateResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MatchRequestCreateResponse invokeAfterSignal(
            CountDownLatch ready,
            CountDownLatch start,
            Long userId,
            Long voteAnswerId
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("매칭 동시 실행 시작 신호를 기다리다 시간 초과되었습니다.");
        }
        return matchService.createMatchRequest(userId, new MatchRequestCreateRequest(voteAnswerId));
    }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String genre = "IT_" + suffix;
        LocalDate voteDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        voteQuestionId = insert("""
                INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date)
                VALUES (?, ?, ?, ?, ?)
                """, "A or B?", "A", "B", genre, voteDate);
        waitingUserId = insertUser(suffix + "a");
        requesterBUserId = insertUser(suffix + "b");
        requesterCUserId = insertUser(suffix + "c");
        waitingAnswerId = insertAnswer(waitingUserId, "A");
        requesterBAnswerId = insertAnswer(requesterBUserId, "B");
        requesterCAnswerId = insertAnswer(requesterCUserId, "B");
        insert("""
                INSERT INTO match_requests (user_id, vote_answer_id, status)
                VALUES (?, ?, 'WAITING')
                """, waitingUserId, waitingAnswerId);
    }

    private Long insertUser(String suffix) {
        return insert("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES (?, ?, ?)
                """, "match-it-" + suffix + "@example.test", "x".repeat(60),
                "i" + suffix.substring(0, 8) + suffix.charAt(suffix.length() - 1));
    }

    private Long insertAnswer(Long userId, String option) {
        return insert("""
                INSERT INTO vote_answers (vote_question_id, user_id, selected_option)
                VALUES (?, ?, ?)
                """, voteQuestionId, userId, option);
    }

    private Long insert(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private List<MatchRequestState> findRequestsForQuestion() {
        return jdbcTemplate.query("""
                SELECT match_request.user_id, match_request.status, match_request.match_id
                FROM match_requests AS match_request
                JOIN vote_answers AS vote_answer
                    ON vote_answer.vote_answer_id = match_request.vote_answer_id
                WHERE vote_answer.vote_question_id = ?
                """, (resultSet, rowNumber) -> new MatchRequestState(
                resultSet.getLong("user_id"), resultSet.getString("status"),
                resultSet.getObject("match_id", Long.class)), voteQuestionId);
    }

    private MatchRequestState findByUserId(List<MatchRequestState> requests, Long userId) {
        return requests.stream().filter(request -> request.userId().equals(userId))
                .findFirst().orElseThrow();
    }

    private record MatchRequestState(Long userId, String status, Long matchId) { }

}
