package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiException;
import com.ktb4.team16.mulo.recommendation.dto.request.CreateRecommendationPlaylistRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationPlaylistCommandService {
    private static final int REQUIRED_TRACK_COUNT = 5;

    private final WeatherService weatherService;
    private final RecommendationAiClient recommendationAiClient;
    private final RecommendationPlaylistWriter writer;
    private final RecommendationPlaylistQueryService queryService;
    private final RecommendationNearbyTracksService nearbyTracksService;
    private final RecommendationPlaceContextService placeContextService;
    private final Clock clock;

    // 현재 KST 문맥의 AI 추천을 저장하거나, 장애·빈 결과에서는 기존 플레이리스트를 유지한다.
    public CreateResult create(Long userId,
            CreateRecommendationPlaylistRequest request) {
        OffsetDateTime requestedAt = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
        var weather = weatherService.getWeather(request.latitude(), request.longitude(), requestedAt);
        BigDecimal latitude = BigDecimal.valueOf(request.latitude());
        BigDecimal longitude = BigDecimal.valueOf(request.longitude());
        var nearbyTracks = nearbyTracksService.findTopTracks(latitude, longitude).stream()
                .map(track -> new RecommendationAiClient.NearbyTrack(track.title(), track.artistName(), track.count()))
                .toList();
        String placeName = placeContextService.findLegalDongName(latitude, longitude).orElse(null);
        var recommendation = recommendationAiClient.recommend(new RecommendationAiClient.RecommendationContext(
                userId, weather.data().weatherCondition(), weather.data().temperature(), requestedAt,
                nearbyTracks, placeName));
        if (recommendation.tracks() == null || recommendation.tracks().size() != REQUIRED_TRACK_COUNT) {
            throw new RecommendationAiException();
        }
        if (recommendation.degraded()) {
            return new CreateResult(queryService.getCurrentPlaylist(userId).playlist(), false);
        }
        return new CreateResult(writer.replace(userId, recommendation.tracks()), true);
    }

    // Controller가 실제 저장 여부에 맞는 HTTP 상태와 응답 메시지를 선택하도록 생성 결과를 전달한다.
    public record CreateResult(RecommendationPlaylistData.Playlist playlist, boolean replaced) { }
}
