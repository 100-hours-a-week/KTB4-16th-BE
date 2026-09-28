package com.ktb4.team16.mulo.recommendation.client;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface RecommendationAiClient {
    // AI Gateway에 추천 문맥을 전달하고 저장 여부를 판단할 수 있는 추천 결과를 반환한다.
    RecommendationResult recommend(RecommendationContext context);

    // AI 명세의 필수 문맥과 선택 문맥을 한 요청 단위로 보관한다.
    record RecommendationContext(Long userId, WeatherCondition weatherCondition, BigDecimal temperature,
            OffsetDateTime requestedAt, List<NearbyTrack> nearbyTracks, String placeName) { }
    // 주변 자물쇠에서 집계한 음악의 최소 AI 전달 형식이다.
    record NearbyTrack(String title, String artistName, long count) { }
    record RecommendationResult(List<RecommendedTrack> tracks, boolean degraded) { }
    record RecommendedTrack(String externalTrackId, String title, String artistName,
            String albumImageUrl, String externalUrl) { }
}
