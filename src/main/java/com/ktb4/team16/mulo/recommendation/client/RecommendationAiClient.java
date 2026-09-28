package com.ktb4.team16.mulo.recommendation.client;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface RecommendationAiClient {
    // AI Gateway에 추천 문맥을 전달하고 저장 여부를 판단할 수 있는 추천 결과를 반환한다.
    RecommendationResult recommend(RecommendationContext context);

    record RecommendationContext(Long userId, WeatherCondition weatherCondition, BigDecimal temperature,
            OffsetDateTime requestedAt, List<NearbyTrack> nearbyTracks) { }
    record NearbyTrack(String title, String artistName, Long count) { }
    record RecommendationResult(List<RecommendedTrack> tracks, boolean degraded) { }
    record RecommendedTrack(String externalTrackId, String title, String artistName,
            String albumImageUrl, String externalUrl) { }
}
