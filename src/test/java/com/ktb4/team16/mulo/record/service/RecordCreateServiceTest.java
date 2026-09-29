package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.client.KakaoRegionLookupException;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class RecordCreateServiceTest {

    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final MusicTrackService musicTrackService = mock(MusicTrackService.class);
    private final RecordPhotoRepository recordPhotoRepository = mock(RecordPhotoRepository.class);
    private final KakaoRegionClient kakaoRegionClient = mock(KakaoRegionClient.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            mock(com.ktb4.team16.mulo.record.cursor.RecordCursorCodec.class),
            userRepository,
            placeRepository,
            weatherService,
            uploadService,
            musicTrackService,
            recordPhotoRepository,
            mock(GcsStorageService.class),
            kakaoRegionClient
    );

    @Test
    void createsRecordPhotoAndDeletesTemporaryMetadataAfterSuccessfulCreation() {
        Long userId = 7L;
        Long uploadId = 11L;
        User user = mock(User.class);
        Place place = mock(Place.class);
        Upload upload = mock(Upload.class);
        MusicTrack musicTrack = mock(MusicTrack.class);
        RecordCreateRequest request = request(uploadId);

        when(userRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(placeRepository.findByLatitudeAndLongitude(
                request.location().latitude(), request.location().longitude()))
                .thenReturn(Optional.of(place));
        when(uploadService.findValidUpload(userId, uploadId)).thenReturn(upload);
        when(musicTrackService.findOrCreate(
                "track-id", "title", "artist", "album-image", "external-url"))
                .thenReturn(musicTrack);
        when(weatherService.getWeather(anyDouble(), anyDouble(), any())).thenThrow(
                new WeatherApiException());
        when(upload.getImageUrl()).thenReturn("uploads/7/photo.jpg");
        when(upload.getMimeType()).thenReturn("image/jpeg");
        when(upload.getFileSize()).thenReturn(123L);

        RecordCreateResponse response = recordService.createRecord(userId, request);

        assertThat(response).isNotNull();
        verify(recordPhotoRepository).save(any(RecordPhoto.class));
        verify(uploadService).deleteMetadata(upload);
        verify(musicTrackService).findOrCreate(
                eq("track-id"), eq("title"), eq("artist"), eq("album-image"), eq("external-url"));
        verifyNoInteractions(kakaoRegionClient);
        verify(placeRepository, never()).save(any());
        ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPlace()).isSameAs(place);
    }

    @Test
    void createsNewPlaceUsingServerRegionInsteadOfClientRegion() {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5000001"), new BigDecimal("127.0000000"),
                        "client-code", "client-name"),
                request(11L).music(), 10, "comment", 11L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(mock(User.class)));
        when(placeRepository.findByLatitudeAndLongitude(
                request.location().latitude(), request.location().longitude()))
                .thenReturn(Optional.empty());
        when(kakaoRegionClient.findLegalRegion(
                request.location().latitude(), request.location().longitude()))
                .thenReturn(new KakaoRegionClient.LegalRegion("1168010100", "역삼동"));
        Upload upload = mock(Upload.class);
        when(uploadService.findValidUpload(7L, 11L)).thenReturn(upload);
        when(upload.getImageUrl()).thenReturn("uploads/7/photo.jpg");
        when(upload.getMimeType()).thenReturn("image/jpeg");
        when(upload.getFileSize()).thenReturn(123L);
        when(weatherService.getWeather(anyDouble(), anyDouble(), any()))
                .thenThrow(new WeatherApiException());
        when(musicTrackService.findOrCreate(
                "track-id", "title", "artist", "album-image", "external-url"))
                .thenReturn(mock(MusicTrack.class));

        recordService.createRecord(7L, request);

        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(placeCaptor.capture());
        Place place = placeCaptor.getValue();
        assertThat(place.getLegalDongCode()).isEqualTo("1168010100");
        assertThat(place.getLegalDongName()).isEqualTo("역삼동");
        assertThat(place.getLatitude()).isEqualTo(request.location().latitude());
        assertThat(place.getLongitude()).isEqualTo(request.location().longitude());
        verify(placeRepository).findByLatitudeAndLongitude(
                request.location().latitude(), request.location().longitude());
        ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPlace()).isSameAs(place);
        verify(recordPhotoRepository).save(any(RecordPhoto.class));
        verify(uploadService).deleteMetadata(upload);
    }

    @ParameterizedTest
    @EnumSource(KakaoRegionLookupException.Reason.class)
    void rejectsNewPlaceWithoutValidRegionBeforeAnyWriteOrUploadConsumption(
            KakaoRegionLookupException.Reason reason
    ) {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("40.7"), new BigDecimal("-74.0"), "forged", "forged"),
                request(11L).music(), 10, "comment", 11L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L))
                .thenReturn(Optional.of(mock(User.class)));
        when(placeRepository.findByLatitudeAndLongitude(
                request.location().latitude(), request.location().longitude()))
                .thenReturn(Optional.empty());
        KakaoRegionLookupException failure = new KakaoRegionLookupException(reason);
        when(kakaoRegionClient.findLegalRegion(
                request.location().latitude(), request.location().longitude())).thenThrow(failure);

        assertThatThrownBy(() -> recordService.createRecord(7L, request)).isSameAs(failure);

        verify(placeRepository, never()).save(any());
        verifyNoInteractions(recordRepository, recordPhotoRepository,
                uploadService, weatherService, musicTrackService);
    }

    private RecordCreateRequest request(Long uploadId) {
        return new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), null, null),
                new RecordCreateRequest.Music(
                        "track-id", "title", "artist", "album-image", "external-url"),
                10,
                "comment",
                uploadId
        );
    }
}
