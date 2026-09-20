package com.ktb4.team16.mulo.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeatherTimePolicyTest {
    private final WeatherTimePolicy policy = new WeatherTimePolicy();

    @ParameterizedTest
    @CsvSource({
            "2026-09-20T11:29:59+09:00, 2026-09-20T11:00:00+09:00[Asia/Seoul]",
            "2026-09-20T11:30:00+09:00, 2026-09-20T12:00:00+09:00[Asia/Seoul]",
            "2026-09-20T23:40:00+09:00, 2026-09-21T00:00:00+09:00[Asia/Seoul]"
    })
    void choosesNearestHour(String requestedAt, String expected) {
        assertThat(policy.nearestForecastAt(OffsetDateTime.parse(requestedAt)))
                .isEqualTo(ZonedDateTime.parse(expected));
    }

    @Test
    void elevenTenStartsWithEightHundredBase() {
        ZonedDateTime now = ZonedDateTime.parse("2026-09-20T11:10:00+09:00[Asia/Seoul]");
        ZonedDateTime target = ZonedDateTime.parse("2026-09-20T11:00:00+09:00[Asia/Seoul]");

        assertThat(policy.candidateBaseTimes(now, target))
                .startsWith(ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]"));
    }

    @Test
    void elevenFortyStartsWithElevenHundredBase() {
        ZonedDateTime now = ZonedDateTime.parse("2026-09-20T11:40:00+09:00[Asia/Seoul]");
        ZonedDateTime target = ZonedDateTime.parse("2026-09-20T12:00:00+09:00[Asia/Seoul]");

        assertThat(policy.candidateBaseTimes(now, target))
                .startsWith(ZonedDateTime.parse("2026-09-20T11:00:00+09:00[Asia/Seoul]"));
    }
}
