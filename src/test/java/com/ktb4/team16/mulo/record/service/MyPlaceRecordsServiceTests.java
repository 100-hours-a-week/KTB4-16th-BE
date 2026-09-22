package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.cursor.RecordCursor;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordsResponseDto;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class MyPlaceRecordsServiceTests {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordCursorCodec cursorCodec = mock(RecordCursorCodec.class);
    private final RecordService recordService = new RecordService(
            recordRepository, cursorCodec);

    @Test
    void returnsTwentyRecordsAndCursorWhenRepositoryReturnsMoreThanTwenty() {
        List<MyPlaceRecordResponseDto> repositoryRecords = records(21);
        when(recordRepository.findMyPlaceRecordsFirstPage(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(10L)),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(repositoryRecords);
        when(cursorCodec.encode(repositoryRecords.get(19).createdAt(), repositoryRecords.get(19).recordId()))
                .thenReturn("next-cursor");

        MyPlaceRecordsResponseDto response = recordService.getMyPlaceRecords(
                1L, List.of(10L), null);

        assertThat(response.records()).hasSize(20);
        assertThat(response.nextCursor()).isEqualTo("next-cursor");
        verify(recordRepository).findMyPlaceRecordsFirstPage(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(10L)),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void usesDecodedCursorForNextPage() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 19, 15, 30);
        when(cursorCodec.decode("cursor-value")).thenReturn(new RecordCursor(createdAt, 101L));
        when(recordRepository.findMyPlaceRecordsAfterCursor(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(10L, 20L)),
                org.mockito.ArgumentMatchers.eq(createdAt),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());

        MyPlaceRecordsResponseDto response = recordService.getMyPlaceRecords(
                1L, List.of(10L, 20L), "cursor-value");

        assertThat(response.records()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        verify(recordRepository).findMyPlaceRecordsAfterCursor(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(List.of(10L, 20L)),
                org.mockito.ArgumentMatchers.eq(createdAt),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    private List<MyPlaceRecordResponseDto> records(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(id -> new MyPlaceRecordResponseDto(
                        (long) id, 10L, 583L, "밤편지", "아이유",
                        LocalDateTime.of(2026, 9, 19, 15, 30).minusMinutes(id)))
                .toList();
    }
}
