package com.ktb4.team16.mulo.weather.controller;

import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weatherService;

    @GetMapping("/api/weather")
    public WeatherResponse getWeather(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0")
            double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0")
            double longitude,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime at) {
        return weatherService.getWeather(latitude, longitude, at);
    }
}
