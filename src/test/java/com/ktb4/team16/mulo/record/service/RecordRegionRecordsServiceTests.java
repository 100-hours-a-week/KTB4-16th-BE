package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class RecordRegionRecordsServiceTests {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordCursorCodec cursorCodec = mock(RecordCursorCodec.class);
    private final RecordService recordService = new RecordService(recordRepository, cursorCodec);

    @Test
    void returnsOnlySelectedRegionRecords() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 25, 12, 0);
        MyPlaceRecordResponseDto record = new MyPlaceRecordResponseDto(
                1L, 10L, 20L, "곡", "가수", createdAt);
        when(recordRepository.findMyRecordsByLegalDongCode(eq(1L), eq("4111710100"), any(Pageable.class)))
                .thenReturn(List.of(record));
        when(recordRepository.findMyRecordRegion(eq(1L), eq("4111710100")))
                .thenReturn(Optional.of(new RecordRegionGroupResponse("4111710100", "영통동", 1L)));

        RecordRegionRecordsData result = recordService.getMyRecords(1L, "4111710100", null);

        assertThat(result.legalDongCode()).isEqualTo("4111710100");
        assertThat(result.legalDongName()).isEqualTo("영통동");
        assertThat(result.recordsCount()).isEqualTo(1L);
        assertThat(result.records()).containsExactly(record);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void unknownCodeQueriesNullRegionAndUsesSentinelResponse() {
        when(recordRepository.findMyRecordsInUnknownRegion(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(recordRepository.findMyUnknownRecordRegion(eq(1L)))
                .thenReturn(Optional.of(new RecordRegionGroupResponse(null, null, 2L)));

        RecordRegionRecordsData result = recordService.getMyRecords(
                1L, RecordRegionGroupResponse.UNKNOWN_LEGAL_DONG_CODE, null);

        assertThat(result.legalDongCode()).isEqualTo("UNKNOWN");
        assertThat(result.legalDongName()).isEqualTo("위치 정보 없음");
        assertThat(result.recordsCount()).isEqualTo(2L);
        assertThat(result.records()).isEmpty();
    }

    @Test
    void invalidCursorUsesExistingCursorExceptionPolicy() {
        when(cursorCodec.decode("invalid")).thenThrow(new InvalidCursorException());

        assertThatThrownBy(() -> recordService.getMyRecords(1L, "4111010100", "invalid"))
                .isInstanceOf(InvalidCursorException.class);
    }
}
