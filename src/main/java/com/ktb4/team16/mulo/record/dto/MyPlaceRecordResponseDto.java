package com.ktb4.team16.mulo.record.dto;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class MyPlaceRecordResponseDto {

    private final Long recordId;
    private final Long placeId;
    private final Long musicTrackId;
    private final String title;
    private final String artistName;
    private final LocalDateTime createdAt;

    public MyPlaceRecordResponseDto(
            Long recordId,
            Long placeId,
            Long musicTrackId,
            String title,
            String artistName,
            LocalDateTime createdAt
    ) {
        this.recordId = recordId;
        this.placeId = placeId;
        this.musicTrackId = musicTrackId;
        this.title = title;
        this.artistName = artistName;
        this.createdAt = createdAt;
    }
}
