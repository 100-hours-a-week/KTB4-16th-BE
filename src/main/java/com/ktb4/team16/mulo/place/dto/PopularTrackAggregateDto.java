package com.ktb4.team16.mulo.place.dto;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class PopularTrackAggregateDto {

    private final Long musicTrackId;
    private final String title;
    private final String artistName;
    private final Long count;
    private final LocalDateTime latestRecordCreatedAt;

    public PopularTrackAggregateDto(
            Long musicTrackId,
            String title,
            String artistName,
            Long count,
            LocalDateTime latestRecordCreatedAt
    ) {
        this.musicTrackId = musicTrackId;
        this.title = title;
        this.artistName = artistName;
        this.count = count;
        this.latestRecordCreatedAt = latestRecordCreatedAt;
    }
}
