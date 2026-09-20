package com.ktb4.team16.mulo.place.dto;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class PopularPlaceMarkerResponseDto {

    private final Long placeId;
    private final String placeName;
    private final String dongName;
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    public PopularPlaceMarkerResponseDto(
            Long placeId,
            String placeName,
            String dongName,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.placeId = placeId;
        this.placeName = placeName;
        this.dongName = dongName;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
