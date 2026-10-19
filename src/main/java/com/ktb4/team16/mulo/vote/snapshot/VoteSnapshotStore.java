package com.ktb4.team16.mulo.vote.snapshot;

import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VoteSnapshotStore {

    private final JdbcTemplate jdbcTemplate;

    public VoteSnapshotStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isReady(LocalDate voteDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vote_daily_snapshots
                WHERE vote_date = ? AND status = 'READY'
                """, Integer.class, voteDate);
        return count != null && count > 0;
    }

    public boolean beginOrLockSnapshot(LocalDate voteDate) {
        jdbcTemplate.update("""
                INSERT INTO vote_daily_snapshots (vote_date, status)
                VALUES (?, 'BUILDING')
                ON DUPLICATE KEY UPDATE vote_date = vote_daily_snapshots.vote_date
                """, voteDate);
        String status = jdbcTemplate.queryForObject("""
                SELECT status FROM vote_daily_snapshots WHERE vote_date = ? FOR UPDATE
                """, String.class, voteDate);
        return "READY".equals(status);
    }

    public Optional<VoteDailyAssignment> findAssignment(LocalDate voteDate, Long userId) {
        return jdbcTemplate.query("""
                        SELECT music_genre, vote_question_id
                        FROM vote_user_daily_assignments
                        WHERE vote_date = ? AND user_id = ?
                        """, resultSet -> resultSet.next()
                        ? Optional.of(new VoteDailyAssignment(resultSet.getString("music_genre"),
                                resultSet.getObject("vote_question_id", Long.class)))
                        : Optional.empty(), voteDate, userId);
    }

    public List<AssignmentRow> findAssignments(LocalDate voteDate) {
        return jdbcTemplate.query("""
                        SELECT user_id, music_genre, vote_question_id
                        FROM vote_user_daily_assignments
                        WHERE vote_date = ?
                        ORDER BY user_id
                        """, (resultSet, rowNumber) -> new AssignmentRow(
                        resultSet.getLong("user_id"), resultSet.getString("music_genre"),
                        resultSet.getObject("vote_question_id", Long.class)), voteDate);
    }

    public Optional<Long> findQuestionId(LocalDate voteDate, String musicGenre) {
        return jdbcTemplate.query("""
                        SELECT vote_question_id
                        FROM vote_questions
                        WHERE vote_date = ? AND music_genre = ?
                        """, resultSet -> resultSet.next()
                        ? Optional.of(resultSet.getLong("vote_question_id")) : Optional.empty(),
                voteDate, musicGenre);
    }

    public boolean hasQuestions(LocalDate voteDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vote_questions WHERE vote_date = ?
                """, Integer.class, voteDate);
        return count != null && count > 0;
    }

    public Optional<VoteQuestionContent> findQuestionContent(LocalDate voteDate, String musicGenre) {
        return jdbcTemplate.query("""
                        SELECT music_genre, question_text, option_a, option_b
                        FROM vote_questions
                        WHERE vote_date = ? AND music_genre = ?
                        """, resultSet -> resultSet.next()
                        ? Optional.of(new VoteQuestionContent(resultSet.getString("music_genre"),
                                resultSet.getString("question_text"), resultSet.getString("option_a"),
                                resultSet.getString("option_b")))
                        : Optional.empty(), voteDate, musicGenre);
    }

    public Map<String, Long> saveQuestions(LocalDate voteDate, List<AiVoteQuestion> questions) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (AiVoteQuestion question : questions.stream()
                .sorted(java.util.Comparator.comparing(AiVoteQuestion::musicGenre)).toList()) {
            ids.put(question.musicGenre(), saveQuestion(voteDate, question));
        }
        return ids;
    }

    public void beginSnapshot(LocalDate voteDate) {
        jdbcTemplate.update("INSERT INTO vote_daily_snapshots (vote_date, status) VALUES (?, 'BUILDING')",
                voteDate);
    }

    public void savePrefetchedAssignments(LocalDate voteDate, List<AssignmentRow> assignments) {
        for (AssignmentRow assignment : assignments) {
            jdbcTemplate.update("""
                            INSERT INTO vote_user_daily_assignments
                                (vote_date, user_id, music_genre, vote_question_id)
                            VALUES (?, ?, ?, NULL)
                            ON DUPLICATE KEY UPDATE
                                user_id = vote_user_daily_assignments.user_id
                            """,
                    voteDate, assignment.userId(), assignment.musicGenre());
        }
    }

    public void saveAssignments(LocalDate voteDate, List<AssignmentRow> assignments) {
        for (AssignmentRow assignment : assignments) {
            jdbcTemplate.update("""
                            INSERT INTO vote_user_daily_assignments
                                (vote_date, user_id, music_genre, vote_question_id)
                            VALUES (?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                user_id = vote_user_daily_assignments.user_id
                            """,
                    voteDate, assignment.userId(), assignment.musicGenre(), assignment.voteQuestionId());
        }
    }

    public void completeAssignments(LocalDate voteDate, List<AssignmentRow> assignments) {
        for (AssignmentRow assignment : assignments) {
            int updated = jdbcTemplate.update("""
                            UPDATE vote_user_daily_assignments
                            SET vote_question_id = ?
                            WHERE vote_date = ? AND user_id = ? AND vote_question_id IS NULL
                            """,
                    assignment.voteQuestionId(), voteDate, assignment.userId());
            if (updated == 0 && findAssignment(voteDate, assignment.userId()).isEmpty()) {
                jdbcTemplate.update("""
                                INSERT INTO vote_user_daily_assignments
                                    (vote_date, user_id, music_genre, vote_question_id)
                                VALUES (?, ?, ?, ?)
                                ON DUPLICATE KEY UPDATE
                                    user_id = vote_user_daily_assignments.user_id
                                """,
                        voteDate, assignment.userId(), assignment.musicGenre(), assignment.voteQuestionId());
            }
        }
    }

    public void markReady(LocalDate voteDate) {
        jdbcTemplate.update("UPDATE vote_daily_snapshots SET status = 'READY' WHERE vote_date = ?",
                voteDate);
    }

    public boolean hasAssignmentsWithoutQuestion(LocalDate voteDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vote_user_daily_assignments
                WHERE vote_date = ? AND vote_question_id IS NULL
                """, Integer.class, voteDate);
        return count != null && count > 0;
    }

    public void assignIfMissing(LocalDate voteDate, Long userId, String musicGenre, Long questionId) {
        jdbcTemplate.update("""
                        INSERT INTO vote_user_daily_assignments
                            (vote_date, user_id, music_genre, vote_question_id)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            user_id = vote_user_daily_assignments.user_id
                        """,
                voteDate, userId, musicGenre, questionId);
    }

    private Long saveQuestion(LocalDate voteDate, AiVoteQuestion question) {
        Optional<ExistingQuestion> existing = findQuestion(voteDate, question.musicGenre());
        if (existing.isPresent()) {
            return existing.get().voteQuestionId();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                                INSERT INTO vote_questions
                                    (question_text, option_a, option_b, music_genre, vote_date)
                                VALUES (?, ?, ?, ?, ?)
                                """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, question.question());
                statement.setString(2, question.optionA());
                statement.setString(3, question.optionB());
                statement.setString(4, question.musicGenre());
                statement.setObject(5, voteDate);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("vote_questions INSERT did not return an ID.");
            }
            return key.longValue();
        } catch (DuplicateKeyException exception) {
            ExistingQuestion concurrent = findQuestionForUpdate(voteDate, question.musicGenre())
                    .orElseThrow(() -> exception);
            return concurrent.voteQuestionId();
        }
    }

    private Optional<ExistingQuestion> findQuestionForUpdate(LocalDate voteDate, String musicGenre) {
        return jdbcTemplate.query("""
                        SELECT vote_question_id, question_text, option_a, option_b
                        FROM vote_questions
                        WHERE vote_date = ? AND music_genre = ?
                        FOR UPDATE
                        """, resultSet -> resultSet.next()
                        ? Optional.of(new ExistingQuestion(resultSet.getLong("vote_question_id"),
                                resultSet.getString("question_text"), resultSet.getString("option_a"),
                                resultSet.getString("option_b")))
                        : Optional.empty(), voteDate, musicGenre);
    }

    private Optional<ExistingQuestion> findQuestion(LocalDate voteDate, String musicGenre) {
        return jdbcTemplate.query("""
                        SELECT vote_question_id, question_text, option_a, option_b
                        FROM vote_questions
                        WHERE vote_date = ? AND music_genre = ?
                        """, resultSet -> resultSet.next()
                        ? Optional.of(new ExistingQuestion(resultSet.getLong("vote_question_id"),
                                resultSet.getString("question_text"), resultSet.getString("option_a"),
                                resultSet.getString("option_b")))
                        : Optional.empty(), voteDate, musicGenre);
    }

    public record AssignmentRow(Long userId, String musicGenre, Long voteQuestionId) { }

    private record ExistingQuestion(Long voteQuestionId, String question, String optionA, String optionB) { }
}
