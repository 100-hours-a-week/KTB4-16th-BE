package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordRegionsServiceTests {
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
    void convertsNullRegionToUnknownApiGroup() {
        when(recordRepository.findMyRecordRegions(1L)).thenReturn(List.of(
                new RecordRegionGroupResponse("4111710100", "영통동", 5L),
                new RecordRegionGroupResponse(null, null, 3L)
        ));

        List<RecordRegionGroupResponse> regions = recordService.getMyRecordRegions(1L);

        assertThat(regions).containsExactly(
                new RecordRegionGroupResponse("4111710100", "영통동", 5L),
                new RecordRegionGroupResponse("UNKNOWN", "위치 정보 없음", 3L));
    }

    @Test
    void returnsEmptyListWhenUserHasNoActiveRecords() {
        when(recordRepository.findMyRecordRegions(1L)).thenReturn(List.of());

        assertThat(recordService.getMyRecordRegions(1L)).isEmpty();
    }
}
