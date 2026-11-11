package com.ktb4.team16.mulo.dailysong.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailySongNotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailySongNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NotificationCandidate> lockDueJobs(LocalDate throughDate, int limit) {
        return jdbcTemplate.query("""
                SELECT j.job_id, j.user_id, j.notification_event_id
                FROM daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                WHERE j.recommendation_date <= ? AND j.status = 'COMPLETED'
                    AND j.notified_at IS NULL AND u.deleted_at IS NULL
                ORDER BY j.recommendation_date, j.job_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (result, row) -> new NotificationCandidate(
                result.getLong("job_id"), result.getLong("user_id"),
                result.getString("notification_event_id")), throughDate, limit);
    }

    public void insertNotifications(List<NotificationCandidate> candidates, String title, String content) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO notifications (user_id, type, source_event_id, title, content)
                VALUES (?, 'DAILY_SONG_READY', ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                NotificationCandidate candidate = candidates.get(index);
                statement.setLong(1, candidate.userId());
                statement.setString(2, candidate.notificationEventId());
                statement.setString(3, title);
                statement.setString(4, content);
            }

            @Override
            public int getBatchSize() {
                return candidates.size();
            }
        });
    }

    public int markNotified(List<Long> jobIds, LocalDateTime notifiedAt) {
        String placeholders = String.join(",", java.util.Collections.nCopies(jobIds.size(), "?"));
        Object[] parameters = new Object[jobIds.size() + 1];
        parameters[0] = notifiedAt;
        for (int index = 0; index < jobIds.size(); index++) {
            parameters[index + 1] = jobIds.get(index);
        }
        return jdbcTemplate.update("""
                UPDATE daily_song_jobs SET notified_at = ?
                WHERE notified_at IS NULL AND status = 'COMPLETED' AND job_id IN (
                """ + placeholders + ")", parameters);
    }

    public record NotificationCandidate(long jobId, long userId, String notificationEventId) {
    }
}
