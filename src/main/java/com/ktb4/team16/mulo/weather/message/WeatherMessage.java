package com.ktb4.team16.mulo.weather.message;

public enum WeatherMessage {
    WEATHER_RETRIEVED("날씨 조회 성공");

    private final String message;

    WeatherMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
