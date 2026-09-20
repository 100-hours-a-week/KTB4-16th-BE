package com.ktb4.team16.mulo.place.dto;

import java.time.LocalDateTime;

public record PopularTrackAggregateDto(
            Long musicTrackId,
            String title,
            String artistName,
            Long count,
            LocalDateTime latestRecordCreatedAt
    ) {
}
