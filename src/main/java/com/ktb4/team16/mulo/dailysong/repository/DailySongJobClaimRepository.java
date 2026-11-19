package com.ktb4.team16.mulo.dailysong.repository;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DailySongJobClaimRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailySongJobClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claim(long jobId, LocalDateTime now, LocalDateTime leaseUntil) {
        String token = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                UPDATE daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                SET j.status = 'PROCESSING', j.claim_token = ?, j.lease_until = ?,
                    j.ai_call_started_at = NULL, j.failure_code = NULL
                WHERE j.job_id = ? AND j.status = 'PENDING' AND u.deleted_at IS NULL
                """, token, leaseUntil, jobId);
        if (updated != 1) {
            return Optional.empty();
        }
        List<Claim> claims = jdbcTemplate.query("""
                SELECT j.job_id, j.user_id, j.recommendation_date, r.target_cutoff_at
                FROM daily_song_jobs j
                JOIN daily_song_runs r ON r.recommendation_date = j.recommendation_date
                WHERE j.job_id = ? AND j.status = 'PROCESSING' AND j.claim_token = ?
                """, (result, row) -> new Claim(result.getLong("job_id"), result.getLong("user_id"),
                result.getDate("recommendation_date").toLocalDate(),
                result.getTimestamp("target_cutoff_at").toLocalDateTime(), token), jobId, token);
        if (claims.size() != 1) {
            throw new IllegalStateException("선점한 오늘의 노래 작업을 조회하지 못했습니다.");
        }
        return Optional.of(claims.getFirst());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelPendingWithdrawn(long jobId, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                SET j.status = 'CANCELLED', j.failure_code = 'USER_WITHDRAWN', j.finished_at = ?
                WHERE j.job_id = ? AND j.status = 'PENDING' AND u.deleted_at IS NOT NULL
                """, now, jobId);
    }

    public Optional<JobState> findState(long jobId) {
        List<JobState> states = jdbcTemplate.query("""
                SELECT status, claim_token, lease_until FROM daily_song_jobs WHERE job_id = ?
                """, (result, row) -> new JobState(DailySongJobStatus.valueOf(result.getString("status")),
                result.getString("claim_token"), result.getTimestamp("lease_until") == null
                        ? null : result.getTimestamp("lease_until").toLocalDateTime()), jobId);
        return states.stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAiCallStarted(Claim claim, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                SET j.ai_call_started_at = ?
                WHERE j.job_id = ? AND j.status = 'PROCESSING' AND j.claim_token = ?
                    AND j.lease_until > ? AND j.ai_call_started_at IS NULL
                    AND u.deleted_at IS NULL
                """, now, claim.jobId(), claim.token(), now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int fail(Claim claim, String failureCode, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                SET j.status = IF(u.deleted_at IS NULL, 'FAILED', 'CANCELLED'),
                    j.failure_code = IF(u.deleted_at IS NULL, ?, 'USER_WITHDRAWN'),
                    j.claim_token = NULL, j.lease_until = NULL, j.finished_at = ?
                WHERE j.job_id = ? AND j.status = 'PROCESSING' AND j.claim_token = ?
                """, failureCode, now, claim.jobId(), claim.token());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpired(long jobId, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                SET j.status = IF(u.deleted_at IS NULL, 'FAILED', 'CANCELLED'),
                    j.failure_code = IF(u.deleted_at IS NULL, 'CLAIM_LEASE_EXPIRED', 'USER_WITHDRAWN'),
                    j.claim_token = NULL, j.lease_until = NULL, j.finished_at = ?
                WHERE j.job_id = ? AND j.status = 'PROCESSING' AND j.lease_until <= ?
                """, now, jobId, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpiredBatch(LocalDateTime now, int limit) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT job_id FROM daily_song_jobs
                WHERE status = 'PROCESSING' AND lease_until <= ?
                ORDER BY lease_until, job_id LIMIT ?
                """, (result, row) -> result.getLong(1), now, limit);
        int recovered = 0;
        for (Long id : ids) {
            recovered += recoverExpired(id, now);
        }
        return recovered;
    }

    public record Claim(long jobId, long userId, LocalDate recommendationDate,
            LocalDateTime targetCutoffAt, String token) {
    }

    public record JobState(DailySongJobStatus status, String claimToken, LocalDateTime leaseUntil) {
    }
}
