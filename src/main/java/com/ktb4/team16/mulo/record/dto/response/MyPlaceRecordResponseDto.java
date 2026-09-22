package com.ktb4.team16.mulo.record.dto.response;

import java.time.LocalDateTime;

public record MyPlaceRecordResponseDto(
            Long recordId,
            Long placeId,
            Long musicTrackId,
            String title,
            String artistName,
            LocalDateTime createdAt
    ) {
}
