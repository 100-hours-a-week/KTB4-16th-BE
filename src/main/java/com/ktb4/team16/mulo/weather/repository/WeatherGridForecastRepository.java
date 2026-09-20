package com.ktb4.team16.mulo.weather.repository;

import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import com.ktb4.team16.mulo.weather.entity.WeatherGridForecast;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherGridForecastRepository
        extends JpaRepository<WeatherGridForecast, Long> {
    Optional<WeatherGridForecast> findByGridAndCacheDateAndForecastAt(
            WeatherGrid grid, LocalDate cacheDate, LocalDateTime forecastAt);

    boolean existsByGridAndCacheDate(WeatherGrid grid, LocalDate cacheDate);
}
