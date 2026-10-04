package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiException;
import com.ktb4.team16.mulo.recommendation.dto.request.CreateRecommendationPlaylistRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationPlaylistCommandServiceTest {
    @Mock private WeatherService weatherService;
    @Mock private RecommendationAiClient recommendationAiClient;
    @Mock private RecommendationNearbyTracksService nearbyTracksService;
    @Mock private RecommendationPlaceContextService placeContextService;
    @Mock private RecommendationPlaylistWriter writer;
    @Mock private RecommendationPlaylistQueryService queryService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    // 정상 AI 추천은 사용자 ID·KST 문맥을 전달한 뒤 새 플레이리스트로 교체한다.
    @Test
    void replacesPlaylistWithNonEmptyNonDegradedRecommendation() {
        RecommendationPlaylistCommandService service = service();
        var created = playlist(4L);
        when(weatherService.getWeather(eq(37.5665), eq(126.9780), any(OffsetDateTime.class)))
                .thenReturn(weather());
        when(nearbyTracksService.findTopTracks(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)))
                .thenReturn(List.of(new RecommendationNearbyTracksService.NearbyTrack("비도 오고 그래서", "헤이즈", 9)));
        when(placeContextService.findLegalDongName(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)))
                .thenReturn(java.util.Optional.of("태평로1가"));
        when(recommendationAiClient.recommend(any())).thenReturn(new RecommendationAiClient.RecommendationResult(
                List.of(new RecommendationAiClient.RecommendedTrack("6mzF8HvHdVrzJNd8M1uFCS",
                        "Beautiful", "Crush", "https://image.example/album.jpg",
                        "https://open.spotify.com/track/6mzF8HvHdVrzJNd8M1uFCS")), false));
        when(writer.replace(eq(7L), any())).thenReturn(created);

        var result = service.create(7L, new CreateRecommendationPlaylistRequest(37.5665, 126.9780));

        ArgumentCaptor<RecommendationAiClient.RecommendationContext> captor =
                ArgumentCaptor.forClass(RecommendationAiClient.RecommendationContext.class);
        verify(recommendationAiClient).recommend(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RecommendationAiClient.RecommendationContext(7L,
                WeatherCondition.CLEAR, BigDecimal.valueOf(20),
                OffsetDateTime.ofInstant(clock.instant(), clock.getZone()),
                List.of(new RecommendationAiClient.NearbyTrack("비도 오고 그래서", "헤이즈", 9)), "태평로1가"));
        verify(writer).replace(eq(7L), any());
        assertThat(result.replaced()).isTrue();
        assertThat(result.playlist()).isEqualTo(created);
    }

    // AI 장애 결과는 기존 플레이리스트를 유지하고 Writer를 호출하지 않는다.
    @Test
    void retainsCurrentPlaylistWhenRecommendationIsDegraded() {
        when(weatherService.getWeather(anyDouble(), anyDouble(), any())).thenReturn(weather());
        when(recommendationAiClient.recommend(any())).thenReturn(
                new RecommendationAiClient.RecommendationResult(List.of(), true));
        when(queryService.getCurrentPlaylist(7L)).thenReturn(new RecommendationPlaylistData(playlist(3L)));

        var result = service().create(7L, new CreateRecommendationPlaylistRequest(37.5665, 126.9780));

        verify(writer, never()).replace(any(), any());
        assertThat(result.replaced()).isFalse();
        assertThat(result.playlist()).isEqualTo(playlist(3L));
    }

    // 정상 응답이지만 추천 곡이 없으면 빈 플레이리스트를 새로 만들지 않는다.
    @Test
    void retainsCurrentPlaylistWhenRecommendationHasNoTracks() {
        when(weatherService.getWeather(anyDouble(), anyDouble(), any())).thenReturn(weather());
        when(recommendationAiClient.recommend(any())).thenReturn(
                new RecommendationAiClient.RecommendationResult(List.of(), false));
        when(queryService.getCurrentPlaylist(7L)).thenReturn(new RecommendationPlaylistData(playlist(3L)));

        var result = service().create(7L, new CreateRecommendationPlaylistRequest(37.5665, 126.9780));

        verify(writer, never()).replace(any(), any());
        assertThat(result.replaced()).isFalse();
        assertThat(result.playlist()).isEqualTo(playlist(3L));
    }

    // AI 연동 실패는 기존 데이터를 변경하지 않고 호출자에게 안전한 예외로 전달한다.
    @Test
    void doesNotReplacePlaylistWhenAiCallFails() {
        when(weatherService.getWeather(anyDouble(), anyDouble(), any())).thenReturn(weather());
        when(recommendationAiClient.recommend(any())).thenThrow(new RecommendationAiException());

        assertThatThrownBy(() -> service().create(7L,
                new CreateRecommendationPlaylistRequest(37.5665, 126.9780)))
                .isInstanceOf(RecommendationAiException.class);

        verify(writer, never()).replace(any(), any());
    }

    // 날씨 조회 실패도 AI 호출과 DB 교체를 시작하기 전에 중단한다.
    @Test
    void doesNotReplacePlaylistWhenWeatherFails() {
        when(weatherService.getWeather(anyDouble(), anyDouble(), any())).thenThrow(new WeatherApiException());

        assertThatThrownBy(() -> service().create(7L,
                new CreateRecommendationPlaylistRequest(37.5665, 126.9780)))
                .isInstanceOf(WeatherApiException.class);

        verify(writer, never()).replace(any(), any());
        verify(nearbyTracksService, never()).findTopTracks(any(), any());
        verify(placeContextService, never()).findLegalDongName(any(), any());
    }

    // 테스트 대상 서비스의 외부 의존성을 명시적으로 조립한다.
    private RecommendationPlaylistCommandService service() {
        return new RecommendationPlaylistCommandService(weatherService, recommendationAiClient, writer,
                queryService, nearbyTracksService, placeContextService, clock);
    }

    // 날씨 API가 반환하는 현재 KST 날씨 데이터를 생성한다.
    private WeatherResponse weather() {
        return new WeatherResponse("ok", new WeatherResponse.WeatherData(
                OffsetDateTime.ofInstant(clock.instant(), clock.getZone()), BigDecimal.valueOf(20),
                WeatherCondition.CLEAR));
    }

    // 저장 또는 유지 결과를 검증할 최소 플레이리스트 데이터를 생성한다.
    private RecommendationPlaylistData.Playlist playlist(Long playlistId) {
        return new RecommendationPlaylistData.Playlist(playlistId, List.of());
    }
}
