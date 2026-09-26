package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.service.WeatherService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordDeleteServiceTest {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final MusicTrackService musicTrackService = mock(MusicTrackService.class);
    private final RecordPhotoRepository recordPhotoRepository = mock(RecordPhotoRepository.class);
    private final GcsStorageService gcsStorageService = mock(GcsStorageService.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            mock(RecordCursorCodec.class),
            userRepository,
            placeRepository,
            weatherService,
            uploadService,
            musicTrackService,
            recordPhotoRepository,
            gcsStorageService
    );

    @Test
    void softDeletesActiveRecordOwnedByUserWithoutTouchingRelatedServices() {
        Record record = mock(Record.class);
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(125L, 7L))
                .thenReturn(Optional.of(record));

        recordService.deleteRecord(7L, 125L);

        verify(record).softDelete(any(LocalDateTime.class));
        verifyNoInteractions(userRepository, placeRepository, weatherService, uploadService,
                musicTrackService, recordPhotoRepository, gcsStorageService);
    }

    @Test
    void mapsMissingDeletedAndOtherUsersRecordsToNotFound() {
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(10L), eq(7L)))
                .thenReturn(Optional.empty());
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(11L), eq(7L)))
                .thenReturn(Optional.empty());
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(12L), eq(7L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.deleteRecord(7L, 10L))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> recordService.deleteRecord(7L, 11L))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> recordService.deleteRecord(7L, 12L))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void rejectsNonPositiveRecordIdBeforeRepositoryLookup() {
        assertThatThrownBy(() -> recordService.deleteRecord(7L, 0L))
                .isInstanceOf(InvalidRecordIdException.class);
        assertThatThrownBy(() -> recordService.deleteRecord(7L, -1L))
                .isInstanceOf(InvalidRecordIdException.class);

        verifyNoInteractions(recordRepository);
    }
}
