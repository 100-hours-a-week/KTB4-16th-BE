package com.ktb4.team16.mulo.recommendation.repository;

import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationFeedbackRepository extends JpaRepository<AiRecommendationFeedback, Long> {
    Optional<AiRecommendationFeedback> findByRecommendationId(Long recommendationId);
}
