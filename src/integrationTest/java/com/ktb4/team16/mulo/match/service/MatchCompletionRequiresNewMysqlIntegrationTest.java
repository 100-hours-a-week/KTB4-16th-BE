package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("integration-test")
class MatchCompletionRequiresNewMysqlIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MatchCompletionService completionService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Storage storage;
    @MockitoBean
    private Clock clock;

    private final List<Long> questionIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void verifiesIsolatedDatabaseAndAppliedMigrations() throws Exception {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-17T15:00:00Z"));
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
    }

    @AfterEach
    void removesOnlyRowsCreatedByThisTest() {
        for (Long questionId : questionIds) {
            jdbcTemplate.update("DELETE FROM match_requests WHERE vote_answer_id IN "
                    + "(SELECT vote_answer_id FROM vote_answers WHERE vote_question_id = ?)", questionId);
            jdbcTemplate.update("DELETE FROM matches WHERE vote_question_id = ?", questionId);
            jdbcTemplate.update("DELETE FROM vote_answers WHERE vote_question_id = ?", questionId);
            jdbcTemplate.update("DELETE FROM vote_questions WHERE vote_question_id = ?", questionId);
        }
        if (!userIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
            jdbcTemplate.update("DELETE FROM users WHERE user_id IN (" + placeholders + ")", userIds.toArray());
        }
    }

    @Test
    void afterCommitCompletionUsesNewTransactionAndPersistsMatchedState() {
        MatchFixture fixture = insertMatchingPair();

        afterCommit(() -> completionService.complete(fixture.matchId(), 701L));

        assertThat(statuses(fixture.matchId())).containsExactly("MATCHED", "MATCHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT chat_room_id FROM matches WHERE match_id = ?", Long.class, fixture.matchId()))
                .isEqualTo(701L);
    }

    @Test
    void afterCommitFailureUsesNewTransactionAndPersistsFailedState() {
        MatchFixture fixture = insertMatchingPair();

        afterCommit(() -> completionService.fail(fixture.matchId()));

        assertThat(statuses(fixture.matchId())).containsExactly("FAILED", "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT chat_room_id FROM matches WHERE match_id = ?", Long.class, fixture.matchId())).isNull();
    }

    private void afterCommit(Runnable callback) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        callback.run();
                    }
                }));
    }

    private MatchFixture insertMatchingPair() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long questionId = insert("""
                INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date)
                VALUES (?, ?, ?, ?, ?)
                """, "A or B?", "A", "B", "TX_" + suffix, LocalDate.of(2026, 10, 17));
        questionIds.add(questionId);
        long matchId = insert("INSERT INTO matches (vote_question_id) VALUES (?)", questionId);
        insertMatchingRequest(questionId, matchId, suffix + "a", "A");
        insertMatchingRequest(questionId, matchId, suffix + "b", "B");
        return new MatchFixture(matchId);
    }

    private void insertMatchingRequest(long questionId, long matchId, String suffix, String option) {
        long userId = insert("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES (?, ?, ?)
                """, "completion-" + suffix + "@example.test", "x".repeat(60), suffix.substring(1));
        userIds.add(userId);
        long answerId = insert("""
                INSERT INTO vote_answers (vote_question_id, user_id, selected_option)
                VALUES (?, ?, ?)
                """, questionId, userId, option);
        insert("""
                INSERT INTO match_requests (user_id, vote_answer_id, status, match_id)
                VALUES (?, ?, 'MATCHING', ?)
                """, userId, answerId, matchId);
    }

    private List<String> statuses(long matchId) {
        return jdbcTemplate.queryForList("""
                SELECT status FROM match_requests WHERE match_id = ? ORDER BY match_request_id ASC
                """, String.class, matchId);
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

    private record MatchFixture(long matchId) {
    }
}
