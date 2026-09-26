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
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class MyPlaceRecordsServiceTests {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordCursorCodec cursorCodec = mock(RecordCursorCodec.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final com.ktb4.team16.mulo.music.service.MusicTrackService musicTrackService =
            mock(com.ktb4.team16.mulo.music.service.MusicTrackService.class);
    private final com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository
            recordPhotoRepository = mock(
                    com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository.class);
    private final GcsStorageService gcsStorageService = mock(GcsStorageService.class);
    private final RecordService recordService = new RecordService(
            recordRepository, cursorCodec, userRepository, placeRepository,
            weatherService, uploadService, musicTrackService, recordPhotoRepository,
            gcsStorageService);

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
