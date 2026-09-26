package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.recommendation.dto.request.CreateRecommendationPlaylistRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationPlaylistCommandService {
    private final WeatherService weatherService;
    private final RecommendationAiClient recommendationAiClient;
    private final RecommendationPlaylistWriter writer;
    private final Clock clock;

    // 현재 KST 날씨 문맥으로 Mock AI 추천을 생성하고 저장한다.
    public RecommendationPlaylistData.Playlist create(Long userId,
            CreateRecommendationPlaylistRequest request) {
        OffsetDateTime requestedAt = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
        var weather = weatherService.getWeather(request.latitude(), request.longitude(), requestedAt);
        var tracks = recommendationAiClient.recommend(new RecommendationAiClient.RecommendationContext(
                weather.data().weatherCondition(), weather.data().temperature(), requestedAt));
        return writer.replace(userId, tracks);
    }
}
