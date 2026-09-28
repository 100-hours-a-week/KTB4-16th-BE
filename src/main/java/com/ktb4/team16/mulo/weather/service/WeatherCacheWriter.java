package com.ktb4.team16.mulo.weather.service;

import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import com.ktb4.team16.mulo.weather.entity.WeatherGridForecast;
import com.ktb4.team16.mulo.weather.repository.WeatherGridForecastRepository;
import com.ktb4.team16.mulo.weather.repository.WeatherGridRepository;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherCacheWriter {
    private final WeatherGridRepository gridRepository;
    private final WeatherGridForecastRepository forecastRepository;

    @Transactional
    public void saveDailyForecasts(GridCoordinate coordinate, LocalDate cacheDate,
            ZonedDateTime baseAt, ZonedDateTime fetchedAt, List<ForecastSlot> slots) {
        WeatherGrid grid = gridRepository.findByGridXAndGridY(coordinate.x(), coordinate.y())
                .orElseGet(() -> gridRepository.saveAndFlush(WeatherGrid.create(coordinate)));
        List<WeatherGridForecast> forecasts = slots.stream()
                .map(slot -> WeatherGridForecast.create(
                        grid, cacheDate, slot,
                        baseAt.toLocalDateTime(), fetchedAt.toLocalDateTime()))
                .toList();
        forecastRepository.saveAll(forecasts);
        forecastRepository.flush();
    }

    // 부분 캐시에 없는 목표 예보 한 건만 추가하고 기존 예보값은 변경하지 않는다.
    @Transactional
    public void saveMissingForecast(WeatherGrid grid, LocalDate cacheDate,
            ZonedDateTime baseAt, ZonedDateTime fetchedAt, ForecastSlot slot) {
        WeatherGridForecast forecast = WeatherGridForecast.create(grid, cacheDate, slot,
                baseAt.toLocalDateTime(), fetchedAt.toLocalDateTime());
        forecastRepository.saveAndFlush(forecast);
    }
}
