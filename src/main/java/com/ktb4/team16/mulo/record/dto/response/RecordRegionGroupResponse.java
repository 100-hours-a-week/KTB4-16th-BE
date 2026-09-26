package com.ktb4.team16.mulo.record.dto.response;

public record RecordRegionGroupResponse(
        String legalDongCode,
        String legalDongName,
        Long recordsCount
) {
    public static final String UNKNOWN_LEGAL_DONG_CODE = "UNKNOWN";
}
