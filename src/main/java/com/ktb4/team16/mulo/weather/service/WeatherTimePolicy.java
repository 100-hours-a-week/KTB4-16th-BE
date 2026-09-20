package com.ktb4.team16.mulo.weather.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class WeatherTimePolicy {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<Integer> BASE_HOURS =
            List.of(2, 5, 8, 11, 14, 17, 20, 23);

    public ZonedDateTime nearestForecastAt(OffsetDateTime requestedAt) {
        ZonedDateTime kst = requestedAt.atZoneSameInstant(KST);
        ZonedDateTime hour = kst.truncatedTo(ChronoUnit.HOURS);
        // 중요: 30분 미만은 현재 정각, 30분 이상은 다음 정각 예보를 사용한다.
        return kst.getMinute() < 30 ? hour : hour.plusHours(1);
    }

    public List<ZonedDateTime> candidateBaseTimes(ZonedDateTime now,
            ZonedDateTime target) {
        List<ZonedDateTime> bases = Stream.of(
                        now.withZoneSameInstant(KST).toLocalDate(),
                        now.withZoneSameInstant(KST).toLocalDate().minusDays(1))
                .flatMap(date -> baseTimes(date).stream())
                // 중요: 첫 예보 슬롯은 발표시각 다음 정각부터이므로 목표시각 이전 회차만 허용한다.
                .filter(base -> !base.isAfter(now)
                        && !base.plusHours(1).isAfter(target))
                .sorted(Comparator.reverseOrder())
                .toList();
        return bases.stream().limit(2).toList();
    }

    private List<ZonedDateTime> baseTimes(LocalDate date) {
        return BASE_HOURS.stream()
                .map(hour -> date.atTime(hour, 0).atZone(KST))
                .toList();
    }
}
