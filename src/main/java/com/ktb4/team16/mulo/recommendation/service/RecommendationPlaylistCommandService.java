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
    private final RecommendationNearbyTracksService nearbyTracksService;
    private final RecommendationPlaceContextService placeContextService;
    private final Clock clock;

    // 현재 KST 문맥으로 화면 세션의 새 추천을 만들고 평가 이력만 기록한다.
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
                nearbyTracks, placeName, request.excludeTrackIds()));
        if (recommendation.tracks() == null || recommendation.tracks().size() != REQUIRED_TRACK_COUNT) {
            throw new RecommendationAiException();
        }
        if (recommendation.degraded()) {
            // 프론트가 자신의 화면 상태를 유지하며 서버는 과거 추천을 복원하지 않는다.
            return new CreateResult(null, false, null, null);
        }
        // 이미 보여준 곡이나 한 응답 안의 중복 곡은 성공 추천으로 저장하지 않는다.
        var trackIds = recommendation.tracks().stream()
                .map(RecommendationAiClient.RecommendedTrack::externalTrackId).toList();
        if (new java.util.HashSet<>(trackIds).size() != REQUIRED_TRACK_COUNT
                || trackIds.stream().anyMatch(request.excludeTrackIds()::contains)) {
            throw new RecommendationAiException();
        }
        var saved = writer.replace(userId, request.recommendationAttempt().byteValue(), recommendation.tracks());
        return new CreateResult(saved.playlist(), true, saved.recommendationId(), recommendation.recommendReason());
    }

    // 새 추천 성공 여부·평가 ID·AI 추천 이유를 Controller에 전달한다.
    public record CreateResult(RecommendationPlaylistData.Playlist playlist, boolean replaced,
            Long recommendationId, String recommendReason) { }
}
