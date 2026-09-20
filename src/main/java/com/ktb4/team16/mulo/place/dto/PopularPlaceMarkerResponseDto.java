package com.ktb4.team16.mulo.place.dto;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class PopularPlaceMarkerResponseDto {

    private final Long placeId;
    private final Long recordCount;
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    public PopularPlaceMarkerResponseDto(
            Long placeId,
            Long recordCount,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.placeId = placeId;
        this.recordCount = recordCount;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
