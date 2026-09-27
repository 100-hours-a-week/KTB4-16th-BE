package com.ktb4.team16.mulo.recommendation.repository;

import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationPlaylistRepository
        extends JpaRepository<RecommendationPlaylist, Long> {

    Optional<RecommendationPlaylist> findByUserUserId(Long userId);
}
