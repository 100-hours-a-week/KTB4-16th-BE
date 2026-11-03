package com.ktb4.team16.mulo.recommendation.entity;

import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_recommendation_results")
@Getter
@NoArgsConstructor
public class AiRecommendationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 10)
    private AiRecommendationType recommendationType;

    @Column(name = "recommendation_attempt")
    private Byte recommendationAttempt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private AiRecommendationResult(
            User user,
            AiRecommendationType recommendationType,
            Byte recommendationAttempt,
            LocalDateTime createdAt
    ) {
        this.user = user;
        this.recommendationType = recommendationType;
        this.recommendationAttempt = recommendationAttempt;
        this.createdAt = createdAt;
    }

    public static AiRecommendationResult create(
            User user,
            AiRecommendationType recommendationType,
            Byte recommendationAttempt,
            LocalDateTime createdAt
    ) {
        return new AiRecommendationResult(user, recommendationType, recommendationAttempt, createdAt);
    }
}
