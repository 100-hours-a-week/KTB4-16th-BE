package com.ktb4.team16.mulo.weather.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse;
import com.ktb4.team16.mulo.weather.dto.WeatherResponse.WeatherData;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.exception.WeatherRequestTimeException;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WeatherControllerTest {
    @Mock private WeatherService weatherService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mvc = MockMvcBuilders.standaloneSetup(new WeatherController(weatherService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsWeatherForRequestedCoordinateAndTime() throws Exception {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-09-20T11:10:00+09:00");
        WeatherResponse response = new WeatherResponse("날씨 조회 성공",
                new WeatherData(
                        OffsetDateTime.parse("2026-09-20T11:00:00+09:00"),
                        new BigDecimal("26.0"), WeatherCondition.CLEAR));
        when(weatherService.getWeather(37.5665, 126.9780, requestedAt))
                .thenReturn(response);

        mvc.perform(get("/api/weather")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("at", "2026-09-20T11:10:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("날씨 조회 성공"))
                .andExpect(jsonPath("$.data.forecastAt")
                        .value("2026-09-20T11:00:00+09:00"))
                .andExpect(jsonPath("$.data.temperature").value(26.0))
                .andExpect(jsonPath("$.data.weatherCondition").value("CLEAR"));
    }

    @Test
    void convertsWeatherFailureToBadGateway() throws Exception {
        when(weatherService.getWeather(
                37.5665, 126.9780,
                OffsetDateTime.parse("2026-09-20T11:10:00+09:00")))
                .thenThrow(new WeatherApiException());

        mvc.perform(get("/api/weather")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("at", "2026-09-20T11:10:00+09:00"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("WEATHER_API_ERROR"));
    }

    @Test
    void convertsPastRequestTimeToBadRequest() throws Exception {
        when(weatherService.getWeather(
                37.5665, 126.9780,
                OffsetDateTime.parse("2000-01-01T10:00:00+09:00")))
                .thenThrow(new WeatherRequestTimeException());

        mvc.perform(get("/api/weather")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("at", "2000-01-01T10:00:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEATHER_REQUEST_TIME"))
                .andExpect(jsonPath("$.message")
                        .value("지난 시간대의 날씨는 조회할 수 없습니다."));
    }

    @Test
    void missingCoordinateReturnsInvalidInput() throws Exception {
        mvc.perform(get("/api/weather")
                        .param("longitude", "126.9780")
                        .param("at", "2026-09-20T11:10:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void malformedDateTimeReturnsInvalidInput() throws Exception {
        mvc.perform(get("/api/weather")
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("at", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
}
