package com.ktb4.team16.mulo.weather.exception;

public class WeatherApiException extends RuntimeException {
    public WeatherApiException() {
        super("Weather data is unavailable");
    }

    public WeatherApiException(Throwable cause) {
        super("Weather data is unavailable", cause);
    }
}
