package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.entity.RecommendationPlaylist;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistItemRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationPlaylistQueryService {
    private final RecommendationPlaylistRepository playlistRepository;
    private final RecommendationPlaylistItemRepository itemRepository;

    // 인증 사용자의 현재 추천 플레이리스트와 저장된 곡 순서를 조회한다.
    @Transactional(readOnly = true)
    public RecommendationPlaylistData getCurrentPlaylist(Long userId) {
        return playlistRepository.findByUserUserId(userId)
                .map(this::toData)
                .orElse(new RecommendationPlaylistData(null));
    }

    // 존재하는 플레이리스트의 곡 항목을 순서대로 응답 데이터로 변환한다.
    private RecommendationPlaylistData toData(RecommendationPlaylist playlist) {
        return RecommendationPlaylistData.from(playlist,
                itemRepository.findAllByPlaylistRecommendationPlaylistIdOrderByMusicOrderAsc(
                        playlist.getRecommendationPlaylistId()));
    }
}
