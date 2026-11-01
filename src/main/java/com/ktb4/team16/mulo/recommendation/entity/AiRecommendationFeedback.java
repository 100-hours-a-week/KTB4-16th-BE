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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_recommendation_feedbacks")
@Getter
@NoArgsConstructor
public class AiRecommendationFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false, unique = true)
    private AiRecommendationResult recommendation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private AiRecommendationFeedbackType feedback;

    @Column(name = "selected_track_id", length = 22)
    private String selectedTrackId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private AiRecommendationFeedback(
            AiRecommendationResult recommendation,
            User user,
            AiRecommendationFeedbackType feedback,
            String selectedTrackId,
            LocalDateTime createdAt
    ) {
        this.recommendation = recommendation;
        this.user = user;
        this.feedback = feedback;
        this.selectedTrackId = selectedTrackId;
        this.createdAt = createdAt;
    }

    public static AiRecommendationFeedback create(
            AiRecommendationResult recommendation,
            User user,
            AiRecommendationFeedbackType feedback,
            String selectedTrackId,
            LocalDateTime createdAt
    ) {
        return new AiRecommendationFeedback(
                recommendation,
                user,
                feedback,
                selectedTrackId,
                createdAt
        );
    }
}
