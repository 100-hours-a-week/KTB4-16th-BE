package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.MyPlaceRecordResponseDto;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.exception.InvalidCursorException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class RecordRegionRecordsServiceTests {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordCursorCodec cursorCodec = mock(RecordCursorCodec.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final MusicTrackService musicTrackService = mock(MusicTrackService.class);
    private final RecordPhotoRepository recordPhotoRepository = mock(RecordPhotoRepository.class);
    private final GcsStorageService gcsStorageService = mock(GcsStorageService.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            cursorCodec,
            userRepository,
            placeRepository,
            weatherService,
            uploadService,
            musicTrackService,
            recordPhotoRepository,
            gcsStorageService);

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
