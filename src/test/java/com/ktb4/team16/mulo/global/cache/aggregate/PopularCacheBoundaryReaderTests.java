package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.Test;

class PopularCacheBoundaryReaderTests {
    private final RecordRepository records = mock(RecordRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-24T23:59:00Z"), ZoneId.of("Asia/Seoul"));

    /** KST JVM의 기록 만료시각을 밀리초로 내려 늦은 캐시 반환을 막는다. */
    @Test
    @ResourceLock("default-time-zone")
    void earliestRecordBoundsValidityUsingServiceTimezone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            LocalDateTime cutoff = LocalDateTime.of(2026, 10, 18, 8, 59);
            when(records.findOldestActiveCreatedAtAtPlaces(List.of(1L), cutoff))
                    .thenReturn(Optional.of(LocalDateTime.of(2026, 10, 18, 9, 0, 0, 500000)));
            PopularCacheBoundaryReader reader = new PopularCacheBoundaryReader(records, clock);

            assertThat(reader.validUntil(List.of(1L), cutoff))
                    .isEqualTo(Instant.parse("2026-10-25T00:00:00Z").toEpochMilli());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    /** JVM이 UTC이면 인기곡 서비스와 같은 UTC 로컬 시각 기준으로 만료를 계산한다. */
    @Test
    @ResourceLock("default-time-zone")
    void utcJvmBoundaryMatchesServiceEvenWhenInjectedClockIsKst() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime cutoff = LocalDateTime.of(2026, 10, 18, 0, 0);
            when(records.findOldestActiveCreatedAtAtPlaces(List.of(1L), cutoff))
                    .thenReturn(Optional.of(LocalDateTime.of(2026, 10, 18, 0, 0, 1)));

            assertThat(new PopularCacheBoundaryReader(records, clock).validUntil(List.of(1L), cutoff))
                    .isEqualTo(Instant.parse("2026-10-25T00:00:01Z").toEpochMilli());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    /** 빈 집계는 시간으로 빠져나갈 기록이 없으므로 일반 TTL과 쓰기 무효화를 따른다. */
    @Test
    void emptyResultHasNoRecordTimeBoundary() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 18, 8, 59);
        when(records.findOldestActiveCreatedAtAtPlaces(List.of(1L), cutoff))
                .thenReturn(Optional.empty());

        assertThat(new PopularCacheBoundaryReader(records, clock).validUntil(List.of(1L), cutoff))
                .isEqualTo(Long.MAX_VALUE);
    }
}
