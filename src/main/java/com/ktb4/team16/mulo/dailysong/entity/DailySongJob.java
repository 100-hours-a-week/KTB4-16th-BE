package com.ktb4.team16.mulo.dailysong.entity;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "daily_song_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_song_jobs_date_user", columnNames = {"recommendation_date", "user_id"}),
        @UniqueConstraint(name = "uk_daily_song_jobs_notification_event", columnNames = "notification_event_id")
}, indexes = {
        @Index(name = "idx_daily_song_jobs_user", columnList = "user_id"),
        @Index(name = "idx_daily_song_jobs_music_track", columnList = "music_track_id"),
        @Index(name = "idx_daily_song_jobs_status_lease", columnList = "status, lease_until"),
        @Index(name = "idx_daily_song_jobs_notification_due", columnList = "status, notified_at, recommendation_date, job_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySongJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_date", insertable = false, updatable = false)
    private DailySongRun run;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private DailySongJobStatus status = DailySongJobStatus.PENDING;

    @Column(name = "claim_token", columnDefinition = "char(36)")
    private String claimToken;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "ai_call_started_at")
    private LocalDateTime aiCallStartedAt;

    @Column(name = "music_track_id")
    private Long musicTrackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_track_id", insertable = false, updatable = false)
    private MusicTrack musicTrack;

    @Column(name = "recommend_reason", columnDefinition = "text")
    private String recommendReason;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "notification_event_id", nullable = false, columnDefinition = "char(36)")
    private String notificationEventId;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public DailySongJob(LocalDate recommendationDate, Long userId, String notificationEventId) {
        this.recommendationDate = recommendationDate;
        this.userId = userId;
        this.notificationEventId = notificationEventId;
    }
}
