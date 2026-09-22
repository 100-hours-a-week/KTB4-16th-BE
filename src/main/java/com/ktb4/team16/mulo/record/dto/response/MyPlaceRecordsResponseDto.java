package com.ktb4.team16.mulo.record.dto.response;

import java.util.List;

public record MyPlaceRecordsResponseDto(
        List<MyPlaceRecordResponseDto> records,
        String nextCursor
) {
}
