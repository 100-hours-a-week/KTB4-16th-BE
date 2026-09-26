package com.ktb4.team16.mulo.recommendation.repository;

import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationPlaylistItemRepository
        extends JpaRepository<RecommendationPlaylistItem, Long> {

    // 플레이리스트의 곡을 저장된 추천 순서대로 조회한다.
    List<RecommendationPlaylistItem> findAllByPlaylistRecommendationPlaylistIdOrderByMusicOrderAsc(
            Long recommendationPlaylistId);
}
