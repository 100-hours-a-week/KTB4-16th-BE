package com.ktb4.team16.mulo.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_recommendation_result_tracks")
@Getter
@NoArgsConstructor
public class AiRecommendationResultTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private AiRecommendationResult recommendation;

    @Column(name = "external_track_id", nullable = false, length = 22)
    private String externalTrackId;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    private AiRecommendationResultTrack(
            AiRecommendationResult recommendation,
            String externalTrackId,
            Integer rank
    ) {
        this.recommendation = recommendation;
        this.externalTrackId = externalTrackId;
        this.rank = rank;
    }

    public static AiRecommendationResultTrack create(
            AiRecommendationResult recommendation,
            String externalTrackId,
            Integer rank
    ) {
        return new AiRecommendationResultTrack(recommendation, externalTrackId, rank);
    }
}
