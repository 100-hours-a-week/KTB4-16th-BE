package com.ktb4.team16.mulo.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KmaForecastMapperTest {
    private final KmaForecastMapper mapper = new KmaForecastMapper();

    @ParameterizedTest
    @CsvSource({
            "0,1,CLEAR",
            "0,3,CLOUDY",
            "0,4,OVERCAST",
            "1,1,RAIN",
            "2,1,RAIN_SNOW",
            "3,1,SNOW",
            "4,1,SHOWER"
    })
    void precipitationTakesPriorityOverSky(String pty, String sky,
            WeatherCondition expected) {
        assertThat(mapper.toCondition(pty, sky)).isEqualTo(expected);
    }
}
