package com.ktb4.team16.mulo.match.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration-test")
class MatchRequestRepositoryTests {

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void verifiesIsolatedDatabaseAndAppliedMigrations() throws Exception {
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
    }

    @Test
    void findsOnlyOppositeWaitingRequestsForSameQuestionInStableFifoOrderUnderLock() {
        long questionId = insertQuestion("MATCH_" + suffix());
        long otherQuestionId = insertQuestion("OTHER_" + suffix());
        long requesterUserId = insertUser();
        long requesterAnswerId = insertAnswer(questionId, requesterUserId, "A", createdAt(10));

        long sameOptionId = insertWaiting(questionId, "A", createdAt(1));
        long otherQuestionIdRequest = insertWaiting(otherQuestionId, "B", createdAt(2));
        long expiredId = insertRequest(questionId, "B", createdAt(3), "EXPIRED", null);

        long matchId = insertMatch(questionId);
        long matchingId = insertRequest(questionId, "B", createdAt(4), "MATCHING", matchId);

        long oldestId = insertWaiting(questionId, "B", createdAt(5));
        long tieFirstId = insertWaiting(questionId, "B", createdAt(6));
        long tieSecondId = insertWaiting(questionId, "B", createdAt(6));

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

        List<MatchRequest> fifo = matchRequestRepository.findOppositeWaitingForUpdate(
                requesterAnswerId, PageRequest.of(0, 10));

        assertThat(fifo)
                .extracting(MatchRequest::getMatchRequestId)
                .containsExactly(oldestId, tieFirstId, tieSecondId);
        assertThat(fifo)
                .extracting(MatchRequest::getMatchRequestId)
                .doesNotContain(sameOptionId, otherQuestionIdRequest, expiredId, matchingId);

        List<MatchRequest> firstCandidate = matchRequestRepository.findOppositeWaitingForUpdate(
                requesterAnswerId, PageRequest.of(0, 1));
        assertThat(firstCandidate)
                .extracting(MatchRequest::getMatchRequestId)
                .containsExactly(oldestId);
    }

    private long insertQuestion(String genre) {
        return insert("""
                INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date)
                VALUES (?, ?, ?, ?, ?)
                """, "A or B?", "A", "B", genre, LocalDate.of(2026, 10, 10));
    }

    private long insertUser() {
        String userSuffix = suffix();
        return insert("""
                INSERT INTO users (email, password_hash, nickname)
                VALUES (?, ?, ?)
                """, userSuffix + "@example.test", "test-password-hash", userSuffix.substring(0, 10));
    }

    private long insertAnswer(long questionId, long userId, String selectedOption, LocalDateTime createdAt) {
        return insert("""
                INSERT INTO vote_answers (vote_question_id, user_id, selected_option, created_at)
                VALUES (?, ?, ?, ?)
                """, questionId, userId, selectedOption, createdAt);
    }

    private long insertWaiting(long questionId, String selectedOption, LocalDateTime createdAt) {
        return insertRequest(questionId, selectedOption, createdAt, "WAITING", null);
    }

    private long insertRequest(
            long questionId,
            String selectedOption,
            LocalDateTime createdAt,
            String status,
            Long matchId
    ) {
        long userId = insertUser();
        long answerId = insertAnswer(questionId, userId, selectedOption, createdAt);
        return insert("""
                INSERT INTO match_requests (user_id, vote_answer_id, status, match_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, answerId, status, matchId, createdAt);
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

    private LocalDateTime createdAt(int minute) {
        return LocalDateTime.of(2026, 10, 10, 9, minute);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
