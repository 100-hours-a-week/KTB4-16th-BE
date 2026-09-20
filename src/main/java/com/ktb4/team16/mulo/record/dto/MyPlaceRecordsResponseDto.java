package com.ktb4.team16.mulo.record.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class MyPlaceRecordsResponseDto {

    private final List<MyPlaceRecordResponseDto> records;
    private final String nextCursor;

    public MyPlaceRecordsResponseDto(
            List<MyPlaceRecordResponseDto> records,
            String nextCursor
    ) {
        this.records = records;
        this.nextCursor = nextCursor;
    }
}
