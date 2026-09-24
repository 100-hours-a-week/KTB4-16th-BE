package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.cursor.RecordCursor;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordsResponseDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordService {

    private static final int PAGE_SIZE = 20;

    private final RecordRepository recordRepository;
    private final RecordCursorCodec recordCursorCodec;

    public MyPlaceRecordsResponseDto getMyPlaceRecords(
            Long userId,
            List<Long> placeIds,
            String cursor
    ) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1);

        List<MyPlaceRecordResponseDto> records;

        if (cursor == null) {
            records = recordRepository.findMyPlaceRecordsFirstPage(
                            userId,
                            placeIds,
                            pageable
                    );
        } else {
            RecordCursor decodedCursor = recordCursorCodec.decode(cursor);

            records = recordRepository.findMyPlaceRecordsAfterCursor(
                            userId,
                            placeIds,
                            decodedCursor.createdAt(),
                            decodedCursor.recordId(),
                            pageable
                    );
        }

        boolean hasNext = records.size() > PAGE_SIZE;

        List<MyPlaceRecordResponseDto> responseRecords;

        if (hasNext) {
            responseRecords = records.subList(0, PAGE_SIZE);
        } else {
            responseRecords = records;
        }

        String nextCursor = null;
        if (hasNext) {
            MyPlaceRecordResponseDto lastRecord = responseRecords.get(responseRecords.size() - 1);

            nextCursor = recordCursorCodec.encode(
                    lastRecord.createdAt(),
                    lastRecord.recordId()
            );
        }

        return new MyPlaceRecordsResponseDto(
                responseRecords,
                nextCursor
        );
    }
}
