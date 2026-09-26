package com.ktb4.team16.mulo.recommendation.controller;

import com.ktb4.team16.mulo.recommendation.dto.request.CreateRecommendationPlaylistRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistResponse;
import com.ktb4.team16.mulo.recommendation.message.RecommendationMessage;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaylistCommandService;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaylistQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/playlists")
@RequiredArgsConstructor
public class RecommendationPlaylistController {
    private final RecommendationPlaylistQueryService queryService;
    private final RecommendationPlaylistCommandService commandService;

    // 인증 사용자가 마지막으로 저장한 플레이리스트만 조회한다.
    @GetMapping
    public RecommendationPlaylistResponse getCurrentPlaylist(
            @AuthenticationPrincipal Long userId) {
        return new RecommendationPlaylistResponse(RecommendationMessage.PLAYLIST_RETRIEVED.message(),
                queryService.getCurrentPlaylist(userId));
    }

    // 좌표를 검증하고 현재 날씨 기반 추천 플레이리스트 생성을 서비스 계층에 위임한다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationPlaylistResponse createPlaylist(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRecommendationPlaylistRequest request) {
        var playlist = commandService.create(userId, request);
        return new RecommendationPlaylistResponse(RecommendationMessage.PLAYLIST_CREATED.message(),
                new RecommendationPlaylistData(playlist));
    }
}
