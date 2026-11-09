package com.ktb4.team16.mulo.dailysong.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DailySongOutboxClaimRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailySongOutboxClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findDueEventIds(LocalDateTime now, int limit) {
        return jdbcTemplate.query("""
                SELECT o.event_id
                FROM daily_song_dispatch_outbox o
                WHERE ((o.status = 'PENDING'
                    AND (o.publish_attempt_count = 0 OR o.next_publish_at <= ?))
                    OR (o.status = 'PUBLISHING' AND o.lease_until <= ?))
                    AND EXISTS (SELECT 1 FROM daily_song_jobs j
                        WHERE j.job_id = o.job_id AND j.status = 'PENDING')
                ORDER BY o.next_publish_at, o.event_id
                LIMIT ?
                """, (result, rowNumber) -> result.getString(1), now, now, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(String eventId, LocalDateTime now, LocalDateTime leaseUntil) {
        List<Candidate> candidates = jdbcTemplate.query("""
                SELECT o.event_id, o.job_id, o.publish_attempt_count
                FROM daily_song_dispatch_outbox o
                WHERE o.event_id = ?
                    AND ((o.status = 'PENDING'
                        AND (o.publish_attempt_count = 0 OR o.next_publish_at <= ?))
                    OR (o.status = 'PUBLISHING' AND o.lease_until <= ?))
                    AND EXISTS (SELECT 1 FROM daily_song_jobs j
                        WHERE j.job_id = o.job_id AND j.status = 'PENDING')
                FOR UPDATE SKIP LOCKED
                """, (result, rowNumber) -> new Candidate(result.getString("event_id"),
                        result.getLong("job_id"), result.getInt("publish_attempt_count")), eventId, now, now);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Candidate candidate = candidates.getFirst();
        String token = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                UPDATE daily_song_dispatch_outbox
                SET status = 'PUBLISHING', claim_token = ?, lease_until = ?,
                    publish_attempt_count = publish_attempt_count + 1, last_error_code = NULL
                WHERE event_id = ?
                """, token, leaseUntil, candidate.eventId());
        if (updated != 1) {
            throw new IllegalStateException("오늘의 노래 발행 작업을 선점하지 못했습니다.");
        }
        return Optional.of(new Claim(candidate.eventId(), candidate.jobId(), candidate.attemptCount() + 1, token));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markPublished(Claim claim, LocalDateTime publishedAt) {
        return jdbcTemplate.update("""
                UPDATE daily_song_dispatch_outbox
                SET status = 'PUBLISHED', published_at = ?, claim_token = NULL,
                    lease_until = NULL, last_error_code = NULL
                WHERE event_id = ? AND status = 'PUBLISHING' AND claim_token = ?
                """, publishedAt, claim.eventId(), claim.token());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int scheduleRetry(Claim claim, LocalDateTime nextPublishAt, String errorCode) {
        return jdbcTemplate.update("""
                UPDATE daily_song_dispatch_outbox
                SET status = 'PENDING', next_publish_at = ?, claim_token = NULL,
                    lease_until = NULL, last_error_code = ?
                WHERE event_id = ? AND status = 'PUBLISHING' AND claim_token = ?
                """, nextPublishAt, errorCode, claim.eventId(), claim.token());
    }

    private record Candidate(String eventId, long jobId, int attemptCount) {
    }

    public record Claim(String eventId, long jobId, int attemptCount, String token) {
    }
}
