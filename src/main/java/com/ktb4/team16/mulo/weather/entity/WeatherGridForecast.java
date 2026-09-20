package com.ktb4.team16.mulo.weather.entity;

import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weather_grid_forecasts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherGridForecast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weather_grid_forecast_id")
    private Long weatherGridForecastId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weather_grid_id", nullable = false)
    private WeatherGrid grid;

    @Column(name = "cache_date", nullable = false)
    private LocalDate cacheDate;

    @Column(name = "forecast_at", nullable = false)
    private LocalDateTime forecastAt;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_condition", nullable = false, length = 20)
    private WeatherCondition weatherCondition;

    @Column(name = "base_at", nullable = false)
    private LocalDateTime baseAt;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    private WeatherGridForecast(WeatherGrid grid, LocalDate cacheDate,
            ForecastSlot slot, LocalDateTime baseAt, LocalDateTime fetchedAt) {
        this.grid = grid;
        this.cacheDate = cacheDate;
        this.forecastAt = slot.forecastAt();
        this.temperature = slot.temperature();
        this.weatherCondition = slot.weatherCondition();
        this.baseAt = baseAt;
        this.fetchedAt = fetchedAt;
    }

    public static WeatherGridForecast create(WeatherGrid grid, LocalDate cacheDate,
            ForecastSlot slot, LocalDateTime baseAt, LocalDateTime fetchedAt) {
        return new WeatherGridForecast(grid, cacheDate, slot, baseAt, fetchedAt);
    }
}
