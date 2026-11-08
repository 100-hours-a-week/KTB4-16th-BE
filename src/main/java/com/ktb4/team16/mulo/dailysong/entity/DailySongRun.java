package com.ktb4.team16.mulo.dailysong.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "daily_song_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySongRun {
    @Id
    @Column(name = "recommendation_date", nullable = false)
    private LocalDate recommendationDate;

    @Column(name = "target_cutoff_at", nullable = false)
    private LocalDateTime targetCutoffAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private DailySongRunStatus status = DailySongRunStatus.CREATING;

    @Column(name = "target_count")
    private Long targetCount;

    @Column(name = "owner_token", columnDefinition = "char(36)")
    private String ownerToken;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public DailySongRun(LocalDate recommendationDate, LocalDateTime targetCutoffAt) {
        this.recommendationDate = recommendationDate;
        this.targetCutoffAt = targetCutoffAt;
    }
}
