package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("integration-test")
class MatchRequestExpirationMysqlIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MatchService matchService;
    @Autowired
    private MatchRequestExpirationService expirationService;
    @Autowired
    private VoteQuestionRepository voteQuestionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Storage storage;
    @MockitoBean
    private Clock clock;

    private final List<Long> voteQuestionIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void verifiesIsolatedDatabaseAndAppliedMigrations() throws Exception {
        when(clock.instant()).thenReturn(LocalDate.now(KST).atTime(12, 0).atZone(KST).toInstant());
        when(clock.getZone()).thenReturn(KST);
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
    }

    @AfterEach
    void removesOnlyRowsCreatedByThisTest() {
        for (Long voteQuestionId : voteQuestionIds) {
            jdbcTemplate.update("DELETE FROM match_requests WHERE vote_answer_id IN "
                    + "(SELECT vote_answer_id FROM vote_answers WHERE vote_question_id = ?)", voteQuestionId);
            jdbcTemplate.update("DELETE FROM matches WHERE vote_question_id = ?", voteQuestionId);
            jdbcTemplate.update("DELETE FROM vote_answers WHERE vote_question_id = ?", voteQuestionId);
            jdbcTemplate.update("DELETE FROM vote_questions WHERE vote_question_id = ?", voteQuestionId);
        }
        if (!userIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
            jdbcTemplate.update("DELETE FROM users WHERE user_id IN (" + placeholders + ")", userIds.toArray());
        }
        voteQuestionIds.clear();
        userIds.clear();
    }

    @Test
    void expiresOnlyTodayWaitingRequests() {
        LocalDate today = LocalDate.now(KST);
        long todayQuestionId = insertQuestion(today, "today");
        long yesterdayQuestionId = insertQuestion(today.minusDays(1), "yesterday");

        long todayWaitingUserId = insertUser("today-waiting");
        long todayWaitingAnswerId = insertAnswer(todayQuestionId, todayWaitingUserId, "A");
        long todayWaitingRequestId = insertRequest(todayWaitingUserId, todayWaitingAnswerId, "WAITING", null);

        long todayExpiredUserId = insertUser("today-expired");
        long todayExpiredAnswerId = insertAnswer(todayQuestionId, todayExpiredUserId, "B");
        long todayExpiredRequestId = insertRequest(todayExpiredUserId, todayExpiredAnswerId, "EXPIRED", null);

        long matchId = insertMatch(todayQuestionId);
        long todayMatchingUserId = insertUser("today-matching");
        long todayMatchingAnswerId = insertAnswer(todayQuestionId, todayMatchingUserId, "A");
        long todayMatchingRequestId = insertRequest(todayMatchingUserId, todayMatchingAnswerId, "MATCHING", matchId);

        long todayMatchedUserId = insertUser("today-matched");
        long todayMatchedAnswerId = insertAnswer(todayQuestionId, todayMatchedUserId, "B");
        long todayMatchedRequestId = insertRequest(todayMatchedUserId, todayMatchedAnswerId, "MATCHED", matchId);

        long yesterdayUserId = insertUser("yesterday-waiting");
        long yesterdayAnswerId = insertAnswer(yesterdayQuestionId, yesterdayUserId, "A");
        long yesterdayRequestId = insertRequest(yesterdayUserId, yesterdayAnswerId, "WAITING", null);

        int expiredCount = expirationService.expireTodayWaitingRequests();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(status(todayWaitingRequestId)).isEqualTo("EXPIRED");
        assertThat(status(todayExpiredRequestId)).isEqualTo("EXPIRED");
        assertThat(status(todayMatchingRequestId)).isEqualTo("MATCHING");
        assertThat(status(todayMatchedRequestId)).isEqualTo("MATCHED");
        assertThat(status(yesterdayRequestId)).isEqualTo("WAITING");
        assertThat(matchIdFor(todayWaitingRequestId)).isNull();
    }

    @RepeatedTest(10)
    void expirationWaitsForInFlightMatchAndDoesNotExpireMatchingRequests() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long questionId = insertQuestion(LocalDate.now(KST), "race-" + suffix);
        long waitingUserId = insertUser("race-a-" + suffix);
        long requesterUserId = insertUser("race-b-" + suffix);
        long waitingAnswerId = insertAnswer(questionId, waitingUserId, "A");
        long requesterAnswerId = insertAnswer(questionId, requesterUserId, "B");
        long waitingRequestId = insertRequest(waitingUserId, waitingAnswerId, "WAITING", null);

        AtomicReference<Instant> now = new AtomicReference<>(
                LocalDate.now(KST).atTime(22, 59, 59).atZone(KST).toInstant());
        Instant cutoff = LocalDate.now(KST).atTime(23, 0).atZone(KST).toInstant();
        CountDownLatch expirationClockRead = new CountDownLatch(1);
        when(clock.instant()).thenAnswer(invocation -> {
            Instant current = now.get();
            if (current.equals(cutoff)) {
                expirationClockRead.countDown();
            }
            return current;
        });

        CountDownLatch matchPrepared = new CountDownLatch(1);
        CountDownLatch allowMatchCommit = new CountDownLatch(1);
        CountDownLatch expirationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            Future<?> matchingFuture = executor.submit(() -> transactionTemplate.execute(status -> {
                voteQuestionRepository.findByVoteQuestionIdForUpdate(questionId).orElseThrow();
                var response = matchService.createMatchRequest(
                        requesterUserId, new MatchRequestCreateRequest(requesterAnswerId));
                matchPrepared.countDown();
                await(allowMatchCommit);
                return response;
            }));

            assertThat(matchPrepared.await(10, TimeUnit.SECONDS)).isTrue();
            now.set(cutoff);
            Future<Integer> expirationFuture = executor.submit(() -> {
                expirationStarted.countDown();
                return expirationService.expireTodayWaitingRequests();
            });
            assertThat(expirationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(expirationClockRead.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> expirationFuture.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowMatchCommit.countDown();

            matchingFuture.get(30, TimeUnit.SECONDS);
            assertThat(expirationFuture.get(30, TimeUnit.SECONDS)).isZero();
        } finally {
            allowMatchCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(status(waitingRequestId)).isEqualTo(MatchRequestStatus.MATCHING.name());
        assertThat(statusByAnswer(requesterAnswerId)).isEqualTo(MatchRequestStatus.MATCHING.name());
        assertThat(matchIdFor(waitingRequestId)).isNotNull()
                .isEqualTo(matchIdByAnswer(requesterAnswerId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE vote_question_id = ?", Integer.class, questionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_requests WHERE vote_answer_id = ? AND status = 'MATCHING'",
                Integer.class, waitingAnswerId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_requests WHERE vote_answer_id = ? AND status = 'EXPIRED'",
                Integer.class, waitingAnswerId)).isZero();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 신호를 기다리다 시간 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    private long insertQuestion(LocalDate voteDate, String suffix) {
        long id = insert("""
                INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date)
                VALUES (?, ?, ?, ?, ?)
                """, "A or B?", "A", "B", "IT_" + suffix, voteDate);
        voteQuestionIds.add(id);
        return id;
    }

    private long insertUser(String suffix) {
        long id = insert("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES (?, ?, ?)
                """, "match-expire-" + suffix + "@example.test", "x".repeat(60),
                "e" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        userIds.add(id);
        return id;
    }

    private long insertAnswer(long questionId, long userId, String option) {
        return insert("""
                INSERT INTO vote_answers (vote_question_id, user_id, selected_option)
                VALUES (?, ?, ?)
                """, questionId, userId, option);
    }

    private long insertRequest(long userId, long answerId, String status, Long matchId) {
        return insert("""
                INSERT INTO match_requests (user_id, vote_answer_id, status, match_id)
                VALUES (?, ?, ?, ?)
                """, userId, answerId, status, matchId);
    }

    private long insertMatch(long questionId) {
        return insert("INSERT INTO matches (vote_question_id) VALUES (?)", questionId);
    }

    private long insert(String sql, Object... values) {
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

    private String status(long matchRequestId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM match_requests WHERE match_request_id = ?", String.class, matchRequestId);
    }

    private Long matchIdFor(long matchRequestId) {
        return jdbcTemplate.queryForObject(
                "SELECT match_id FROM match_requests WHERE match_request_id = ?", Long.class, matchRequestId);
    }

    private String statusByAnswer(long voteAnswerId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM match_requests WHERE vote_answer_id = ?", String.class, voteAnswerId);
    }

    private Long matchIdByAnswer(long voteAnswerId) {
        return jdbcTemplate.queryForObject(
                "SELECT match_id FROM match_requests WHERE vote_answer_id = ?", Long.class, voteAnswerId);
    }
}
