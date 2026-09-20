package com.ktb4.team16.mulo.weather.dto;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.entity.WeatherGridForecast;
import com.ktb4.team16.mulo.weather.message.WeatherMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record WeatherResponse(String message, WeatherData data) {
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

    public static WeatherResponse from(WeatherGridForecast forecast) {
        return new WeatherResponse(
                WeatherMessage.WEATHER_RETRIEVED.message(),
                new WeatherData(
                        forecast.getForecastAt().atOffset(KST_OFFSET),
                        forecast.getTemperature(),
                        forecast.getWeatherCondition()));
    }

    public record WeatherData(OffsetDateTime forecastAt, BigDecimal temperature,
            WeatherCondition weatherCondition) {
    }
}
