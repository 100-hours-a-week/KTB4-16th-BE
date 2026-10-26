package com.ktb4.team16.mulo.global.cache.aggregate;

import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("popular-cache")
@ConditionalOnProperty(prefix = "mulo.aggregate-cache", name = "enabled", havingValue = "true")
public class PopularCacheBoundaryReader {
    private final RecordRepository records;
    private final Clock clock;

    /** 기존 인기곡 서비스의 JVM 기본 시간대와 같은 기준으로 DB 시각을 해석한다. */
    public PopularCacheBoundaryReader(RecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock.withZone(ZoneId.systemDefault());
    }

    /** 인기곡 결과를 재사용할 수 있는 마지막 시각을 epoch 밀리초로 계산한다. */
    @Transactional(readOnly = true)
    public long validUntil(List<Long> placeIds, LocalDateTime cutoff) {
        return records.findOldestActiveCreatedAtAtPlaces(placeIds, cutoff)
                .map(createdAt -> createdAt.plusDays(7).atZone(clock.getZone())
                        .toInstant().toEpochMilli())
                .orElse(Long.MAX_VALUE);
    }
}
