package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.cursor.RecordCursor;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.cursor.RecordCursorPagination;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordsResponseDto;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordService {

    private final RecordRepository recordRepository;
    private final RecordCursorCodec recordCursorCodec;

    public MyPlaceRecordsResponseDto getMyPlaceRecords(
            Long userId,
            List<Long> placeIds,
            String cursor
    ) {
        Pageable pageable = PageRequest.of(0, RecordCursorPagination.fetchSize());

        List<MyPlaceRecordResponseDto> records;

        if (cursor == null) {
            records =
                    recordRepository.findMyPlaceRecordsFirstPage(
                            userId,
                            placeIds,
                            pageable
                    );
        } else {
            RecordCursor decodedCursor = recordCursorCodec.decode(cursor);

            records =
                    recordRepository.findMyPlaceRecordsAfterCursor(
                            userId,
                            placeIds,
                            decodedCursor.createdAt(),
                            decodedCursor.recordId(),
                            pageable
                    );
        }

        RecordCursorPagination.CursorPage<MyPlaceRecordResponseDto> page =
                RecordCursorPagination.paginate(
                        records,
                        recordCursorCodec,
                        MyPlaceRecordResponseDto::createdAt,
                        MyPlaceRecordResponseDto::recordId);

        return new MyPlaceRecordsResponseDto(
                page.records(),
                page.nextCursor()
        );
    }

    public List<RecordRegionGroupResponse> getMyRecordRegions(Long userId) {
        return recordRepository.findMyRecordRegions(userId).stream()
                .map(this::toApiRegionGroup)
                .toList();
    }

    public RecordRegionRecordsData getMyRecords(
            Long userId,
            String legalDongCode,
            String cursor
    ) {
        Pageable pageable = PageRequest.of(0, RecordCursorPagination.fetchSize());
        List<MyPlaceRecordResponseDto> records;

        if (cursor == null) {
            records = findRecordsByRegion(userId, legalDongCode, pageable);
        } else {
            RecordCursor decodedCursor = recordCursorCodec.decode(cursor);
            records = findRecordsByRegionAfterCursor(
                    userId,
                    legalDongCode,
                    decodedCursor,
                    pageable
            );
        }

        RecordCursorPagination.CursorPage<MyPlaceRecordResponseDto> page =
                RecordCursorPagination.paginate(
                        records,
                        recordCursorCodec,
                        MyPlaceRecordResponseDto::createdAt,
                        MyPlaceRecordResponseDto::recordId);

        Optional<RecordRegionGroupResponse> region = findRecordRegion(userId, legalDongCode);
        String responseCode = RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)
                ? RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE
                : legalDongCode;
        String responseName = RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)
                ? "위치 정보 없음"
                : region.map(RecordRegionGroupResponse::legalDongName).orElse(null);
        Long recordsCount = region.map(RecordRegionGroupResponse::recordsCount).orElse(0L);

        return new RecordRegionRecordsData(
                responseCode,
                responseName,
                recordsCount,
                page.records(),
                page.nextCursor());
    }

    private Optional<RecordRegionGroupResponse> findRecordRegion(Long userId, String legalDongCode) {
        if (RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)) {
            return recordRepository.findMyUnknownRecordRegion(userId);
        }
        return recordRepository.findMyRecordRegion(userId, legalDongCode);
    }

    private List<MyPlaceRecordResponseDto> findRecordsByRegion(
            Long userId,
            String legalDongCode,
            Pageable pageable
    ) {
        if (RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)) {
            return recordRepository.findMyRecordsInUnknownRegion(userId, pageable);
        }
        return recordRepository.findMyRecordsByLegalDongCode(userId, legalDongCode, pageable);
    }

    private List<MyPlaceRecordResponseDto> findRecordsByRegionAfterCursor(
            Long userId,
            String legalDongCode,
            RecordCursor cursor,
            Pageable pageable
    ) {
        if (RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE.equals(legalDongCode)) {
            return recordRepository.findMyRecordsInUnknownRegionAfterCursor(
                    userId, cursor.createdAt(), cursor.recordId(), pageable);
        }
        return recordRepository.findMyRecordsByLegalDongCodeAfterCursor(
                userId, legalDongCode, cursor.createdAt(), cursor.recordId(), pageable);
    }

    private RecordRegionGroupResponse toApiRegionGroup(RecordRegionGroupResponse regionGroup) {
        if (Objects.isNull(regionGroup.legalDongCode())) {
            return new RecordRegionGroupResponse(
                    RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE,
                    "위치 정보 없음",
                    regionGroup.recordsCount());
        }
        return regionGroup;
    }
}
