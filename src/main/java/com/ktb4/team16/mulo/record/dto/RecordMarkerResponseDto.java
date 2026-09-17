package com.ktb4.team16.mulo.record.dto;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class RecordMarkerResponseDto {

    private Long recordId;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public RecordMarkerResponseDto(
            Long recordId,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.recordId = recordId;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
