package com.ktb4.team16.mulo.dailysong.repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
public class DailySongJobCreationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DailySongJobCreationRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claimRun(LocalDate date, LocalDateTime cutoff, Duration leaseDuration) {
        requirePositiveLease(leaseDuration);
        jdbcTemplate.update("""
                INSERT INTO daily_song_runs (recommendation_date, target_cutoff_at)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE recommendation_date = recommendation_date
                """, date, cutoff);
        RunState run = lockRun(date);
        if (!run.cutoff().equals(cutoff)) {
            throw new IllegalStateException("오늘의 노래 작업 기준 시각이 기존 실행과 다릅니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!"CREATING".equals(run.status())
                || (run.leaseUntil() != null && run.leaseUntil().isAfter(now))) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                UPDATE daily_song_runs
                SET owner_token = ?, lease_until = ?
                WHERE recommendation_date = ? AND status = 'CREATING'
                """, token, now.plus(leaseDuration), date);
        if (updated != 1) {
            throw new IllegalStateException("오늘의 노래 실행 권한을 선점하지 못했습니다.");
        }
        return Optional.of(new Claim(date, cutoff, token));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeBatch(Claim claim, List<Long> userIds, Duration leaseDuration) {
        requirePositiveLease(leaseDuration);
        RunState run = lockRun(claim.date());
        requireOwner(run, claim, LocalDateTime.now(clock));
        for (Long userId : userIds) {
            UserState user = findUser(userId);
            if (user.createdAt().isAfter(claim.cutoff())
                    || (user.deletedAt() != null && !user.deletedAt().isAfter(claim.cutoff()))) {
                throw new IllegalStateException("오늘의 노래 대상이 아닌 사용자가 작업 생성에 포함됐습니다.");
            }
            JobState job = findJob(claim.date(), userId)
                    .orElseGet(() -> createJob(claim, userId, user, LocalDateTime.now(clock)));
            ensureOutbox(job);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requireOwner(run, claim, now);
        int updated = jdbcTemplate.update("""
                UPDATE daily_song_runs SET lease_until = ?
                WHERE recommendation_date = ? AND status = 'CREATING' AND owner_token = ?
                """, now.plus(leaseDuration), claim.date(), claim.token());
        if (updated != 1) {
            throw new IllegalStateException("오늘의 노래 실행 권한의 만료 시각을 갱신하지 못했습니다.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markReady(Claim claim, long targetCount) {
        if (targetCount < 0) {
            throw new IllegalArgumentException("오늘의 노래 대상 사용자 수는 0 이상이어야 합니다.");
        }
        RunState run = lockRun(claim.date());
        if (!isOwner(run, claim, LocalDateTime.now(clock))) {
            return false;
        }
        Long jobCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM daily_song_jobs WHERE recommendation_date = ?
                """, Long.class, claim.date());
        Long outboxCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM daily_song_dispatch_outbox o
                JOIN daily_song_jobs j ON j.job_id = o.job_id
                WHERE j.recommendation_date = ?
                """, Long.class, claim.date());
        if (jobCount == null || outboxCount == null || jobCount != targetCount || outboxCount != targetCount) {
            throw new IllegalStateException("오늘의 노래 작업과 발행 기록의 수가 대상 사용자 수와 다릅니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!isOwner(run, claim, now)) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE daily_song_runs
                SET status = 'READY', target_count = ?, finalized_at = ?, owner_token = NULL, lease_until = NULL
                WHERE recommendation_date = ? AND status = 'CREATING' AND owner_token = ?
                """, targetCount, now, claim.date(), claim.token()) == 1;
    }

    private void requireOwner(RunState run, Claim claim, LocalDateTime now) {
        if (!isOwner(run, claim, now)) {
            throw new IllegalStateException("오늘의 노래 실행 권한이 만료되었거나 다른 인스턴스로 이전됐습니다.");
        }
    }

    private boolean isOwner(RunState run, Claim claim, LocalDateTime now) {
        return "CREATING".equals(run.status()) && claim.cutoff().equals(run.cutoff())
                && claim.token().equals(run.ownerToken())
                && run.leaseUntil() != null && run.leaseUntil().isAfter(now);
    }

    private RunState lockRun(LocalDate date) {
        return jdbcTemplate.query("""
                SELECT status, target_cutoff_at, owner_token, lease_until
                FROM daily_song_runs WHERE recommendation_date = ? FOR UPDATE
                """, (result, row) -> new RunState(result.getString("status"),
                result.getTimestamp("target_cutoff_at").toLocalDateTime(), result.getString("owner_token"),
                toLocalDateTime(result.getTimestamp("lease_until"))), date).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("오늘의 노래 실행 정보를 찾지 못했습니다."));
    }

    private UserState findUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("오늘의 노래 대상 사용자 ID가 없습니다.");
        }
        return jdbcTemplate.query("""
                SELECT created_at, deleted_at FROM users WHERE user_id = ?
                """, (result, row) -> new UserState(result.getTimestamp("created_at").toLocalDateTime(),
                toLocalDateTime(result.getTimestamp("deleted_at"))), userId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("오늘의 노래 대상 사용자를 찾지 못했습니다."));
    }

    private Optional<JobState> findJob(LocalDate date, long userId) {
        return jdbcTemplate.query("""
                SELECT job_id, status FROM daily_song_jobs
                WHERE recommendation_date = ? AND user_id = ?
                """, (result, row) -> new JobState(result.getLong("job_id"), result.getString("status")),
                date, userId).stream().findFirst();
    }

    private JobState createJob(Claim claim, long userId, UserState user, LocalDateTime now) {
        boolean withdrawn = user.deletedAt() != null;
        String status = withdrawn ? "CANCELLED" : "PENDING";
        jdbcTemplate.update("""
                INSERT INTO daily_song_jobs
                    (recommendation_date, user_id, status, notification_event_id, failure_code, finished_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, claim.date(), userId, status, UUID.randomUUID().toString(),
                withdrawn ? "USER_WITHDRAWN" : null, withdrawn ? now : null);
        return findJob(claim.date(), userId)
                .orElseThrow(() -> new IllegalStateException("오늘의 노래 작업을 저장하지 못했습니다."));
    }

    private void ensureOutbox(JobState job) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM daily_song_dispatch_outbox WHERE job_id = ?
                """, Integer.class, job.id());
        if (count != null && count > 0) {
            return;
        }
        if (!"PENDING".equals(job.status()) && !"CANCELLED".equals(job.status())) {
            throw new IllegalStateException("종결된 오늘의 노래 작업에 발행 기록이 없습니다.");
        }
        jdbcTemplate.update("""
                INSERT INTO daily_song_dispatch_outbox (event_id, job_id, status)
                VALUES (?, ?, ?)
                """, UUID.randomUUID().toString(), job.id(), job.status());
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static void requirePositiveLease(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("오늘의 노래 실행 권한의 유효 시간은 0보다 커야 합니다.");
        }
    }

    public record Claim(LocalDate date, LocalDateTime cutoff, String token) {
    }

    private record RunState(String status, LocalDateTime cutoff, String ownerToken, LocalDateTime leaseUntil) {
    }

    private record UserState(LocalDateTime createdAt, LocalDateTime deletedAt) {
    }

    private record JobState(long id, String status) {
    }
}
