package com.ktb4.team16.mulo.recommendation.client;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface RecommendationAiClient {
    // 추천 문맥에 맞는 내부 추천 곡 목록을 반환한다.
    List<RecommendedTrack> recommend(RecommendationContext context);

    record RecommendationContext(WeatherCondition weatherCondition, BigDecimal temperature,
            OffsetDateTime requestedAt) { }
    record RecommendedTrack(String externalTrackId, String title, String artistName,
            String albumImageUrl, String externalUrl) { }
}
