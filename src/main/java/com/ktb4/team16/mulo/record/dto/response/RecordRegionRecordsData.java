package com.ktb4.team16.mulo.record.dto.response;

import java.util.List;

public record RecordRegionRecordsData(
        String legalDongCode,
        String legalDongName,
        Long recordsCount,
        List<MyPlaceRecordResponseDto> records,
        String nextCursor
) {
}
