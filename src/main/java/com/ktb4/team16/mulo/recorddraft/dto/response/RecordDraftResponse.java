package com.ktb4.team16.mulo.recorddraft.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecordDraftResponse(String message, Data data) {
    public record Data(
            Location location,
            Music music,
            Byte moodScore,
            String comment,
            Long uploadId,
            String photoUrl,
            LocalDateTime expiresAt
    ) { }

    public record Location(
            BigDecimal latitude,
            BigDecimal longitude,
            String legalDongCode,
            String legalDongName
    ) { }

    public record Music(
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl
    ) { }
}
