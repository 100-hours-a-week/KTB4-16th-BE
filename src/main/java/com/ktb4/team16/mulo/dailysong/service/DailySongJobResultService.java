package com.ktb4.team16.mulo.dailysong.service;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySongJobResultService {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DailySongJobResultService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public CompletionOutcome complete(Claim claim, RecommendedTrack track, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<JobState> states = jdbcTemplate.query("""
                SELECT j.status, j.claim_token, j.lease_until, u.deleted_at
                FROM daily_song_jobs j
                JOIN users u ON u.user_id = j.user_id
                WHERE j.job_id = ? FOR UPDATE
                """, (result, row) -> new JobState(result.getString("status"),
                result.getString("claim_token"), result.getTimestamp("lease_until") == null
                        ? null : result.getTimestamp("lease_until").toLocalDateTime(),
                result.getTimestamp("deleted_at") != null), claim.jobId());
        if (states.size() != 1) {
            return CompletionOutcome.NOT_APPLIED;
        }
        JobState state = states.getFirst();
        if (!"PROCESSING".equals(state.status()) || !claim.token().equals(state.claimToken())
                || state.leaseUntil() == null || !state.leaseUntil().isAfter(now)) {
            return CompletionOutcome.NOT_APPLIED;
        }
        if (state.withdrawn()) {
            int updated = jdbcTemplate.update("""
                    UPDATE daily_song_jobs
                    SET status = 'CANCELLED', failure_code = 'USER_WITHDRAWN',
                        claim_token = NULL, lease_until = NULL, finished_at = ?
                    WHERE job_id = ? AND status = 'PROCESSING' AND claim_token = ?
                    """, now, claim.jobId(), claim.token());
            if (updated != 1) {
                throw new IllegalStateException("오늘의 노래 추천 작업의 취소 상태를 저장하지 못했습니다.");
            }
            return CompletionOutcome.CANCELLED;
        }
        jdbcTemplate.update("""
                INSERT INTO music_tracks
                    (external_track_id, title, artist_name, album_image_url, external_url, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE music_track_id = music_track_id
                """, track.externalTrackId(), track.title(), track.artistName(),
                track.albumImageUrl(), track.externalUrl(), now);
        Long musicTrackId = jdbcTemplate.queryForObject("""
                SELECT music_track_id FROM music_tracks WHERE external_track_id = ?
                """, Long.class, track.externalTrackId());
        if (musicTrackId == null) {
            throw new IllegalStateException("오늘의 노래 추천곡을 저장하지 못했습니다.");
        }
        int updated = jdbcTemplate.update("""
                UPDATE daily_song_jobs
                SET status = 'COMPLETED', music_track_id = ?, recommend_reason = ?,
                    failure_code = NULL, claim_token = NULL, lease_until = NULL, finished_at = ?
                WHERE job_id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, musicTrackId, reason, now, claim.jobId(), claim.token());
        if (updated != 1) {
            throw new IllegalStateException("오늘의 노래 추천 결과를 완료 상태로 저장하지 못했습니다.");
        }
        return CompletionOutcome.COMPLETED;
    }

    public enum CompletionOutcome {
        COMPLETED, CANCELLED, NOT_APPLIED
    }

    private record JobState(String status, String claimToken, LocalDateTime leaseUntil, boolean withdrawn) {
    }
}
