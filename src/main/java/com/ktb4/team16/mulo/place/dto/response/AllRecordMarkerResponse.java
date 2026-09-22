package com.ktb4.team16.mulo.place.dto.response;

import java.math.BigDecimal;

public record AllRecordMarkerResponse(
            Long placeId,
            Long recordsCount,
            String legalDongCode,
            String legalDongName,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
}
