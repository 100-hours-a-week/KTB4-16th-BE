package com.ktb4.team16.mulo.dailysong.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.dailysong.repository.DailySongRecordLocationRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongRecordLocationRepository.Coordinates;
import com.ktb4.team16.mulo.recommendation.service.RecommendationNearbyTracksService;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaceContextService;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DailySongRecommendationContextServiceTest {
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private final DailySongRecordLocationRepository locations = mock(DailySongRecordLocationRepository.class);
    private final WeatherService weather = mock(WeatherService.class);
    private final RecommendationPlaceContextService places = mock(RecommendationPlaceContextService.class);
    private final RecommendationNearbyTracksService nearby = mock(RecommendationNearbyTracksService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), KST);
    private final DailySongRecommendationContextService service = new DailySongRecommendationContextService(
            locations, weather, places, nearby, clock);
    private final Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10),
            LocalDateTime.of(2026, 11, 10, 0, 30), "token");

    @Test
    void usesLatestValidRecordLocationAtCutoffAndFixedRecommendationTime() {
        Coordinates coordinates = new Coordinates(new BigDecimal("37.5"), new BigDecimal("127.0"));
        when(locations.findLatestValid(13, claim.targetCutoffAt())).thenReturn(Optional.of(coordinates));
        when(weather.getWeather(37.5, 127.0, OffsetDateTime.now(clock))).thenReturn(response());
        when(places.findLegalDongName(coordinates.latitude(), coordinates.longitude()))
                .thenReturn(Optional.of("태평로1가"));
        when(nearby.findTopTracks(coordinates.latitude(), coordinates.longitude())).thenReturn(List.of(
                new RecommendationNearbyTracksService.NearbyTrack("테스트 곡", "테스트 가수", 2)));

        var context = service.build(claim);

        assertThat(context.userId()).isEqualTo(13);
        assertThat(context.requestedAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T00:30:00+09:00"));
        assertThat(context.weatherCondition()).isEqualTo(WeatherCondition.CLEAR);
        assertThat(context.temperature()).isEqualByComparingTo("20");
        assertThat(context.placeName()).isEqualTo("태평로1가");
        assertThat(context.nearbyTracks()).hasSize(1);
        assertThat(context.excludeTrackIds()).isEmpty();
        verify(locations).findLatestValid(13, claim.targetCutoffAt());
    }

    @Test
    void fallsBackToSeoulCityHallOnlyWhenLocationIsAbsent() {
        Coordinates cityHall = new Coordinates(new BigDecimal("37.5663"), new BigDecimal("126.9779"));
        when(locations.findLatestValid(13, claim.targetCutoffAt())).thenReturn(Optional.empty());
        when(weather.getWeather(37.5663, 126.9779, OffsetDateTime.now(clock))).thenReturn(response());
        when(places.findLegalDongName(cityHall.latitude(), cityHall.longitude())).thenReturn(Optional.empty());

        var context = service.build(claim);

        assertThat(context.placeName()).isNull();
        verify(nearby).findTopTracks(cityHall.latitude(), cityHall.longitude());
    }

    @Test
    void doesNotHideLocationDatabaseFailureWithFallback() {
        when(locations.findLatestValid(13, claim.targetCutoffAt()))
                .thenThrow(new DataAccessResourceFailureException("위치 조회 DB 연결이 실패했습니다."));

        assertThatThrownBy(() -> service.build(claim))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private WeatherResponse response() {
        return new WeatherResponse("날씨 조회 성공", new WeatherResponse.WeatherData(
                OffsetDateTime.parse("2026-11-10T15:00:00+09:00"),
                new BigDecimal("20"), WeatherCondition.CLEAR));
    }
}
