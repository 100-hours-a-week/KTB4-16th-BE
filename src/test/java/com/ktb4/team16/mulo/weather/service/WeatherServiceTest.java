package com.ktb4.team16.mulo.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.weather.client.KmaWeatherClient;
import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import com.ktb4.team16.mulo.weather.entity.WeatherGridForecast;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.repository.WeatherGridForecastRepository;
import com.ktb4.team16.mulo.weather.repository.WeatherGridRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {
    private static final GridCoordinate COORDINATE =
            new GridCoordinate((short) 60, (short) 127);
    private static final OffsetDateTime REQUESTED_AT =
            OffsetDateTime.parse("2026-09-20T11:10:00+09:00");
    private static final ZonedDateTime TARGET =
            ZonedDateTime.parse("2026-09-20T11:00:00+09:00[Asia/Seoul]");

    @Mock private GridCoordinateConverter coordinateConverter;
    @Mock private WeatherTimePolicy timePolicy;
    @Mock private WeatherGridRepository gridRepository;
    @Mock private WeatherGridForecastRepository forecastRepository;
    @Mock private KmaWeatherClient client;
    @Mock private WeatherCacheWriter cacheWriter;

    private WeatherService service;
    private WeatherGrid grid;

    @BeforeEach
    void setUp() {
        service = new WeatherService(coordinateConverter, timePolicy,
                gridRepository, forecastRepository, client, cacheWriter,
                Clock.fixed(Instant.parse("2026-09-20T02:10:00Z"), ZoneId.of("Asia/Seoul")));
        grid = WeatherGrid.create(COORDINATE);
    }

    @Test
    void returnsDailyCacheWithoutCallingKma() {
        givenDefaultCacheLookup();
        WeatherGridForecast cached = forecastAt(LocalDateTime.of(2026, 9, 20, 11, 0));
        when(forecastRepository.findByGridAndCacheDateAndForecastAt(
                grid, LocalDate.of(2026, 9, 20), TARGET.toLocalDateTime()))
                .thenReturn(Optional.of(cached));

        WeatherResponse response = service.getWeather(37.5665, 126.9780, REQUESTED_AT);

        assertThat(response.data().forecastAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-20T11:00:00+09:00"));
        verify(client, never()).fetch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotCallKmaWhenDailyBatchExistsWithoutTargetSlot() {
        givenDefaultCacheLookup();
        LocalDate cacheDate = LocalDate.of(2026, 9, 20);
        when(forecastRepository.findByGridAndCacheDateAndForecastAt(
                grid, cacheDate, TARGET.toLocalDateTime())).thenReturn(Optional.empty());
        when(forecastRepository.existsByGridAndCacheDate(grid, cacheDate)).thenReturn(true);

        assertThatThrownBy(() -> service.getWeather(37.5665, 126.9780, REQUESTED_AT))
                .isInstanceOf(WeatherApiException.class);
        verify(client, never()).fetch(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPastRequestBeforeReadingWeatherCache() {
        OffsetDateTime pastRequestedAt = OffsetDateTime.parse("2000-01-01T10:00:00+09:00");

        assertThatThrownBy(() -> service.getWeather(37.5665, 126.9780, pastRequestedAt))
                .isInstanceOf(WeatherApiException.class);

        verifyNoInteractions(coordinateConverter, timePolicy, gridRepository,
                forecastRepository, client, cacheWriter);
    }

    @Test
    void fallsBackOnceAndStoresAllFutureSlots() {
        when(coordinateConverter.convert(37.5665, 126.9780)).thenReturn(COORDINATE);
        when(timePolicy.nearestForecastAt(REQUESTED_AT)).thenReturn(TARGET);
        ZonedDateTime requestedKst = REQUESTED_AT.atZoneSameInstant(ZoneId.of("Asia/Seoul"));
        ZonedDateTime first = ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]");
        ZonedDateTime second = ZonedDateTime.parse("2026-09-20T05:00:00+09:00[Asia/Seoul]");
        ForecastSlot targetSlot = new ForecastSlot(TARGET.toLocalDateTime(),
                new BigDecimal("25.0"), WeatherCondition.CLEAR);
        WeatherGridForecast stored = forecastAt(TARGET.toLocalDateTime());
        when(gridRepository.findByGridXAndGridY((short) 60, (short) 127))
                .thenReturn(Optional.empty(), Optional.of(grid));
        when(timePolicy.candidateBaseTimes(requestedKst, TARGET))
                .thenReturn(List.of(first, second));
        when(client.fetch(COORDINATE, first)).thenThrow(new WeatherApiException());
        when(client.fetch(COORDINATE, second)).thenReturn(List.of(targetSlot));
        when(forecastRepository.findByGridAndCacheDateAndForecastAt(
                grid, LocalDate.of(2026, 9, 20), TARGET.toLocalDateTime()))
                .thenReturn(Optional.of(stored));

        WeatherResponse response = service.getWeather(37.5665, 126.9780, REQUESTED_AT);

        assertThat(response.data().forecastAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-20T11:00:00+09:00"));
        verify(client).fetch(COORDINATE, first);
        verify(client).fetch(COORDINATE, second);
    }

    @Test
    void reloadsCacheAfterConcurrentUniqueConflict() {
        when(coordinateConverter.convert(37.5665, 126.9780)).thenReturn(COORDINATE);
        when(timePolicy.nearestForecastAt(REQUESTED_AT)).thenReturn(TARGET);
        ZonedDateTime requestedKst = REQUESTED_AT.atZoneSameInstant(ZoneId.of("Asia/Seoul"));
        ZonedDateTime baseAt = ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]");
        ForecastSlot targetSlot = new ForecastSlot(TARGET.toLocalDateTime(),
                new BigDecimal("25.0"), WeatherCondition.CLEAR);
        WeatherGridForecast stored = forecastAt(TARGET.toLocalDateTime());
        when(gridRepository.findByGridXAndGridY((short) 60, (short) 127))
                .thenReturn(Optional.empty(), Optional.of(grid));
        when(timePolicy.candidateBaseTimes(requestedKst, TARGET))
                .thenReturn(List.of(baseAt));
        when(client.fetch(COORDINATE, baseAt)).thenReturn(List.of(targetSlot));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(cacheWriter).saveDailyForecasts(
                        org.mockito.ArgumentMatchers.eq(COORDINATE),
                        org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 9, 20)),
                        org.mockito.ArgumentMatchers.eq(baseAt),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.<ForecastSlot>anyList());
        when(forecastRepository.findByGridAndCacheDateAndForecastAt(
                grid, LocalDate.of(2026, 9, 20), TARGET.toLocalDateTime()))
                .thenReturn(Optional.of(stored));

        WeatherResponse response = service.getWeather(37.5665, 126.9780, REQUESTED_AT);

        assertThat(response.data().forecastAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-20T11:00:00+09:00"));
        verify(client).fetch(COORDINATE, baseAt);
    }

    private WeatherGridForecast forecastAt(LocalDateTime forecastAt) {
        ForecastSlot slot = new ForecastSlot(
                forecastAt, new BigDecimal("25.0"), WeatherCondition.CLEAR);
        return WeatherGridForecast.create(
                grid, LocalDate.of(2026, 9, 20), slot,
                LocalDateTime.of(2026, 9, 20, 8, 0),
                LocalDateTime.of(2026, 9, 20, 11, 10));
    }

    private void givenDefaultCacheLookup() {
        when(coordinateConverter.convert(37.5665, 126.9780)).thenReturn(COORDINATE);
        when(timePolicy.nearestForecastAt(REQUESTED_AT)).thenReturn(TARGET);
        when(gridRepository.findByGridXAndGridY((short) 60, (short) 127))
                .thenReturn(Optional.of(grid));
    }
}
