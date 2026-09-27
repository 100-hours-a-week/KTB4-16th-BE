package com.ktb4.team16.mulo.record.dto.response;

import com.ktb4.team16.mulo.record.entity.Record;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecordDetailData(
        Long recordId,
        Long userId,
        Place place,
        Music music,
        Record.WeatherCondition weatherCondition,
        BigDecimal temperature,
        Byte moodScore,
        String comment,
        String photoUrl,
        LocalDateTime createdAt
) {
    public record Place(
            Long placeId,
            String legalDongName,
            BigDecimal latitude,
            BigDecimal longitude,
            String legalDongCode
    ) {
    }

    public record Music(
            Long musicTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl
    ) {
    }
}
