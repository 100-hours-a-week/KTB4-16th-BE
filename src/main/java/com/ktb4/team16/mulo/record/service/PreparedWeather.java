package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.entity.Record;
import java.math.BigDecimal;

public record PreparedWeather(BigDecimal temperature, Record.WeatherCondition weatherCondition) {
    public static PreparedWeather withoutWeather() {
        return new PreparedWeather(null, null);
    }
}
