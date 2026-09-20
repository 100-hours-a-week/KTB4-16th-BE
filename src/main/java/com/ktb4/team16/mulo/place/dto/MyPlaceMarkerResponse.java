package com.ktb4.team16.mulo.place.dto;

import java.math.BigDecimal;

public record MyPlaceMarkerResponse(
            Long placeId,
            String legalDongName,
            Long myRecordsCount,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
}
