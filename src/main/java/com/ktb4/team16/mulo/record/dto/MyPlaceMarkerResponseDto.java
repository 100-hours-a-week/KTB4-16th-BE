package com.ktb4.team16.mulo.record.dto;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class MyPlaceMarkerResponseDto {

    private final Long placeId;
    private final String legalDongName;
    private final Long myRecordsCount;
    private final BigDecimal latitude;
    private final BigDecimal longitude;

    public MyPlaceMarkerResponseDto(
            Long placeId,
            String legalDongName,
            Long myRecordsCount,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.placeId = placeId;
        this.legalDongName = legalDongName;
        this.myRecordsCount = myRecordsCount;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
