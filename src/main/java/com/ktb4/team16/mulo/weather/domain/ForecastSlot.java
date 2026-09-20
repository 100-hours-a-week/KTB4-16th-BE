package com.ktb4.team16.mulo.weather.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForecastSlot(
        LocalDateTime forecastAt,
        BigDecimal temperature,
        WeatherCondition weatherCondition) {
}
