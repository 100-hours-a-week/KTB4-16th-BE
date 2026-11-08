package com.ktb4.team16.mulo.dailysong.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "daily_song_dispatch_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_song_dispatch_outbox_job", columnNames = "job_id")
}, indexes = {
        @Index(name = "idx_daily_song_dispatch_outbox_publish_due", columnList = "status, next_publish_at, lease_until")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySongDispatchOutbox {
    @Id
    @Column(name = "event_id", nullable = false, columnDefinition = "char(36)")
    private String eventId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", insertable = false, updatable = false)
    private DailySongJob job;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private DailySongDispatchOutboxStatus status = DailySongDispatchOutboxStatus.PENDING;

    @Column(name = "publish_attempt_count", nullable = false)
    private Integer publishAttemptCount = 0;

    @Generated(event = EventType.INSERT)
    @Column(name = "next_publish_at", nullable = false, insertable = false)
    private LocalDateTime nextPublishAt;

    @Column(name = "claim_token", columnDefinition = "char(36)")
    private String claimToken;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public DailySongDispatchOutbox(String eventId, Long jobId) {
        this.eventId = eventId;
        this.jobId = jobId;
    }
}
