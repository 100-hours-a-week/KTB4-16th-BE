package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

class RecordCreateServiceTest {

    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final MusicTrackService musicTrackService = mock(MusicTrackService.class);
    private final RecordPhotoRepository recordPhotoRepository = mock(RecordPhotoRepository.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            mock(com.ktb4.team16.mulo.record.cursor.RecordCursorCodec.class),
            userRepository,
            placeRepository,
            uploadService,
            musicTrackService,
            recordPhotoRepository,
            mock(GcsStorageService.class)
    );

    @BeforeEach
    void setUpRecordRepository() {
        when(recordRepository.save(any(Record.class))).thenAnswer(invocation -> {
            Record saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "recordId", 1024L);
            return saved;
        });
    }

    @Test
    void reusesPlaceFoundAgainInsideTransactionAndCreatesRecordPhoto() {
        Long userId = 7L;
        Long uploadId = 11L;
        User user = mock(User.class);
        Place place = mock(Place.class);
        Upload upload = mock(Upload.class);
        MusicTrack musicTrack = mock(MusicTrack.class);
        RecordCreateRequest request = request(uploadId);

        when(userRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(userId);
        when(placeRepository.findByLatitudeAndLongitude(
                request.location().latitude(), request.location().longitude()))
                .thenReturn(Optional.of(place));
        when(uploadService.findValidUpload(userId, uploadId)).thenReturn(upload);
        when(musicTrackService.findOrCreate(
                "track-id", "title", "artist", "album-image", "external-url"))
                .thenReturn(musicTrack);
        when(musicTrack.getTitle()).thenReturn("title");
        when(musicTrack.getArtistName()).thenReturn("artist");
        when(musicTrack.getExternalTrackId()).thenReturn("track-id");
        when(upload.getImageUrl()).thenReturn("uploads/7/photo.jpg");
        when(upload.getMimeType()).thenReturn("image/jpeg");
        when(upload.getFileSize()).thenReturn(123L);

        PreparedWeather preparedWeather = new PreparedWeather(
                new BigDecimal("21.5"), Record.WeatherCondition.CLEAR);
        RecordEmbeddingSnapshot response = recordService.createRecord(userId, request,
                new KakaoRegionClient.LegalRegion("region-code", "region-name"),
                preparedWeather);

        assertThat(response.recordId()).isEqualTo(1024L);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.photoObjectKey()).isEqualTo("uploads/7/photo.jpg");
        assertThat(response.track()).isEqualTo(
                new RecordEmbeddingSnapshot.Track("title", "artist", "track-id"));
        assertThat(response.comment()).isEqualTo("comment");
        assertThat(response.createdAt()).isNotNull();
        verify(recordPhotoRepository).save(any(RecordPhoto.class));
        verify(uploadService).deleteMetadata(upload);
        verify(musicTrackService).findOrCreate(
                eq("track-id"), eq("title"), eq("artist"), eq("album-image"), eq("external-url"));
        verify(placeRepository, org.mockito.Mockito.never()).save(any());
        ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
        verify(recordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPlace()).isSameAs(place);
        assertThat(recordCaptor.getValue().getTemperature()).isEqualByComparingTo("21.5");
        assertThat(recordCaptor.getValue().getWeatherCondition())
                .isEqualTo(Record.WeatherCondition.CLEAR);
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
        Upload upload = mock(Upload.class);
        when(uploadService.findValidUpload(7L, 11L)).thenReturn(upload);
        when(upload.getImageUrl()).thenReturn("uploads/7/photo.jpg");
        when(upload.getMimeType()).thenReturn("image/jpeg");
        when(upload.getFileSize()).thenReturn(123L);
        when(musicTrackService.findOrCreate(
                "track-id", "title", "artist", "album-image", "external-url"))
                .thenReturn(mock(MusicTrack.class));

        recordService.createRecord(7L, request,
                new KakaoRegionClient.LegalRegion("1168010100", "역삼동"),
                PreparedWeather.withoutWeather());

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
