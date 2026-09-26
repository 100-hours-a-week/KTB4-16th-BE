package com.ktb4.team16.mulo.recommendation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.recommendation.dto.request.CreateRecommendationPlaylistRequest;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationPlaylistCommandServiceTest {
    @Mock private WeatherService weatherService;
    @Mock private RecommendationAiClient recommendationAiClient;
    @Mock private RecommendationPlaylistWriter writer;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void usesServerKstNowForWeatherThenStoresMockTracks() {
        RecommendationPlaylistCommandService service = new RecommendationPlaylistCommandService(
                weatherService, recommendationAiClient, writer, clock);
        var request = new CreateRecommendationPlaylistRequest(37.5665, 126.9780);
        when(weatherService.getWeather(eq(37.5665), eq(126.9780), any(OffsetDateTime.class)))
                .thenReturn(new WeatherResponse("ok", new WeatherResponse.WeatherData(
                        OffsetDateTime.ofInstant(clock.instant(), clock.getZone()), BigDecimal.valueOf(20),
                        WeatherCondition.CLEAR)));
        when(recommendationAiClient.recommend(any())).thenReturn(java.util.List.of());

        service.create(7L, request);

        verify(weatherService).getWeather(37.5665, 126.9780,
                OffsetDateTime.ofInstant(clock.instant(), clock.getZone()));
        verify(writer).replace(eq(7L), any());
    }

    @Test
    void doesNotReplacePlaylistWhenWeatherFails() {
        RecommendationPlaylistCommandService service = new RecommendationPlaylistCommandService(
                weatherService, recommendationAiClient, writer, clock);
        when(weatherService.getWeather(anyDouble(), anyDouble(), any())).thenThrow(
                new WeatherApiException());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(7L,
                new CreateRecommendationPlaylistRequest(37.5665, 126.9780)))
                .isInstanceOf(WeatherApiException.class);

        verify(writer, never()).replace(any(), any());
    }
}
