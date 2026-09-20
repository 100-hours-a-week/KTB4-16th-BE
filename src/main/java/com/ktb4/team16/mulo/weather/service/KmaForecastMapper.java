package com.ktb4.team16.mulo.weather.service;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import org.springframework.stereotype.Component;

@Component
public class KmaForecastMapper {
    public WeatherCondition toCondition(String precipitationType, String sky) {
        return switch (precipitationType) {
            case "1" -> WeatherCondition.RAIN;
            case "2" -> WeatherCondition.RAIN_SNOW;
            case "3" -> WeatherCondition.SNOW;
            case "4" -> WeatherCondition.SHOWER;
            case "0" -> skyCondition(sky);
            default -> throw new IllegalArgumentException("Unsupported PTY value");
        };
    }

    private WeatherCondition skyCondition(String sky) {
        return switch (sky) {
            case "1" -> WeatherCondition.CLEAR;
            case "3" -> WeatherCondition.CLOUDY;
            case "4" -> WeatherCondition.OVERCAST;
            default -> throw new IllegalArgumentException("Unsupported SKY value");
        };
    }
}
