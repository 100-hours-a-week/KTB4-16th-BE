package com.ktb4.team16.mulo.recommendation.repository;

import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationResultRepository extends JpaRepository<AiRecommendationResult, Long> {
    Optional<AiRecommendationResult> findByIdAndUserUserId(Long id, Long userId);
}
