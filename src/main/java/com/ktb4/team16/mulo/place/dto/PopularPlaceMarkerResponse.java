package com.ktb4.team16.mulo.place.dto;

import java.math.BigDecimal;

public record PopularPlaceMarkerResponse(
            Long placeId,
            Long recordCount,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
}
