package com.ktb4.team16.mulo.record.dto;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class MyPlaceMarkerResponseDto {

    private final Long placeId;
    private final String placeName;
    private final String dongName;
    private final Long myRecordsCount;
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    public MyPlaceMarkerResponseDto(
            Long placeId,
            String placeName,
            String dongName,
            Long myRecordsCount,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.placeId = placeId;
        this.placeName = placeName;
        this.dongName = dongName;
        this.myRecordsCount = myRecordsCount;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
