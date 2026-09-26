package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.RecordDetailData;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.entity.RecordPhoto;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordDetailServiceTests {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordPhotoRepository recordPhotoRepository = mock(RecordPhotoRepository.class);
    private final GcsStorageService gcsStorageService = mock(GcsStorageService.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            mock(RecordCursorCodec.class),
            mock(UserRepository.class),
            mock(PlaceRepository.class),
            mock(WeatherService.class),
            mock(UploadService.class),
            mock(MusicTrackService.class),
            recordPhotoRepository,
            gcsStorageService
    );

    @Test
    void returnsActiveRecordOwnedByUser() {
        Record record = mock(Record.class);
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(10L, 35L))
                .thenReturn(Optional.of(record));

        assertThat(recordService.findActiveRecordForOwner(35L, 10L)).isSameAs(record);
        verify(recordRepository).findByRecordIdAndUser_UserIdAndDeletedAtIsNull(10L, 35L);
    }

    @Test
    void returnsDetailDataAndSignedPhotoUrlForActiveRecordOwnedByUser() {
        Record record = detailRecord();
        RecordPhoto photo = mock(RecordPhoto.class);
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(10L, 35L))
                .thenReturn(Optional.of(record));
        when(recordPhotoRepository.findByRecord_RecordId(10L)).thenReturn(Optional.of(photo));
        when(photo.getImageUrl()).thenReturn("uploads/35/photo.jpg");
        when(gcsStorageService.createReadSignedUrl("uploads/35/photo.jpg"))
                .thenReturn("https://signed.example/photo");

        RecordDetailData detail = recordService.getRecordDetail(35L, 10L);

        assertThat(detail.recordId()).isEqualTo(10L);
        assertThat(detail.userId()).isEqualTo(35L);
        assertThat(detail.place()).isEqualTo(new RecordDetailData.Place(
                20L, "테스트동", new BigDecimal("37.5000000"),
                new BigDecimal("127.0000000"), "1111010100"));
        assertThat(detail.music()).isEqualTo(new RecordDetailData.Music(
                30L, "title", "artist", "album-image", "external-url"));
        assertThat(detail.weatherCondition()).isEqualTo(Record.WeatherCondition.CLEAR);
        assertThat(detail.temperature()).isEqualByComparingTo("20.5");
        assertThat(detail.moodScore()).isEqualTo((byte) 10);
        assertThat(detail.comment()).isEqualTo("comment");
        assertThat(detail.photoUrl()).isEqualTo("https://signed.example/photo");
        assertThat(detail.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 26, 12, 0));
        verify(gcsStorageService).createReadSignedUrl("uploads/35/photo.jpg");
    }

    @Test
    void returnsNullPhotoUrlWhenRecordHasNoPhoto() {
        Record record = detailRecord();
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(10L, 35L))
                .thenReturn(Optional.of(record));
        when(recordPhotoRepository.findByRecord_RecordId(10L)).thenReturn(Optional.empty());

        RecordDetailData detail = recordService.getRecordDetail(35L, 10L);

        assertThat(detail.photoUrl()).isNull();
        verifyNoInteractions(gcsStorageService);
    }

    @Test
    void rejectsNonPositiveRecordIdBeforeRepositoryLookup() {
        assertThatThrownBy(() -> recordService.getRecordDetail(35L, 0L))
                .isInstanceOf(InvalidRecordIdException.class);
        assertThatThrownBy(() -> recordService.getRecordDetail(35L, -1L))
                .isInstanceOf(InvalidRecordIdException.class);
    }

    @Test
    void mapsMissingDeletedOrOtherUsersRecordToNotFound() {
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(10L), eq(35L)))
                .thenReturn(Optional.empty());
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(11L), eq(35L)))
                .thenReturn(Optional.empty());
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(12L), eq(35L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.getRecordDetail(35L, 10L))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> recordService.getRecordDetail(35L, 11L))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> recordService.getRecordDetail(35L, 12L))
                .isInstanceOf(RecordNotFoundException.class);
    }

    private Record detailRecord() {
        Record record = mock(Record.class);
        User user = mock(User.class);
        Place place = mock(Place.class);
        MusicTrack musicTrack = mock(MusicTrack.class);
        when(record.getRecordId()).thenReturn(10L);
        when(record.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(35L);
        when(record.getPlace()).thenReturn(place);
        when(place.getPlaceId()).thenReturn(20L);
        when(place.getLegalDongName()).thenReturn("테스트동");
        when(place.getLatitude()).thenReturn(new BigDecimal("37.5000000"));
        when(place.getLongitude()).thenReturn(new BigDecimal("127.0000000"));
        when(place.getLegalDongCode()).thenReturn("1111010100");
        when(record.getMusicTrack()).thenReturn(musicTrack);
        when(musicTrack.getMusicTrackId()).thenReturn(30L);
        when(musicTrack.getTitle()).thenReturn("title");
        when(musicTrack.getArtistName()).thenReturn("artist");
        when(musicTrack.getAlbumImageUrl()).thenReturn("album-image");
        when(musicTrack.getExternalUrl()).thenReturn("external-url");
        when(record.getWeatherCondition()).thenReturn(Record.WeatherCondition.CLEAR);
        when(record.getTemperature()).thenReturn(new BigDecimal("20.5"));
        when(record.getMoodScore()).thenReturn((byte) 10);
        when(record.getComment()).thenReturn("comment");
        when(record.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 9, 26, 12, 0));
        return record;
    }
}
