package com.ktb4.team16.mulo.recommendation.repository;

import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResultTrack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationResultTrackRepository
        extends JpaRepository<AiRecommendationResultTrack, Long> {

    List<AiRecommendationResultTrack> findAllByRecommendationIdOrderByRankAsc(Long recommendationId);
}
