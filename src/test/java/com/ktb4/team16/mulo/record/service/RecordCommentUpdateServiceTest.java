package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.service.MusicTrackService;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.cursor.RecordCursorCodec;
import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
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
import org.mockito.ArgumentCaptor;

class RecordCommentUpdateServiceTest {
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            mock(RecordCursorCodec.class),
            mock(UserRepository.class),
            mock(PlaceRepository.class),
            mock(WeatherService.class),
            mock(UploadService.class),
            mock(MusicTrackService.class),
            mock(RecordPhotoRepository.class),
            mock(GcsStorageService.class)
    );

    @Test
    void updatesCommentAndUpdatedAtForActiveRecordOwnedByUser() {
        Record record = record(125L);
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(125L, 7L))
                .thenReturn(Optional.of(record));
        ArgumentCaptor<LocalDateTime> updatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        RecordCommentUpdateResponse response = recordService.updateRecordComment(7L, 125L, "수정된 코멘트");

        assertThat(response).isEqualTo(new RecordCommentUpdateResponse(125L, "수정된 코멘트"));
        verify(record).updateComment(eq("수정된 코멘트"), updatedAtCaptor.capture());
        assertThat(updatedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void allowsNullCommentToDeleteExistingComment() {
        Record record = record(125L);
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(125L, 7L))
                .thenReturn(Optional.of(record));

        RecordCommentUpdateResponse response = recordService.updateRecordComment(7L, 125L, null);

        assertThat(response).isEqualTo(new RecordCommentUpdateResponse(125L, null));
        verify(record).updateComment(eq(null), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void preservesEmptyComment() {
        Record record = record(125L);
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(125L, 7L))
                .thenReturn(Optional.of(record));

        RecordCommentUpdateResponse response = recordService.updateRecordComment(7L, 125L, "");

        assertThat(response).isEqualTo(new RecordCommentUpdateResponse(125L, ""));
        verify(record).updateComment(eq(""), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void mapsMissingDeletedAndOtherUsersRecordsToNotFound() {
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(10L), eq(7L)))
                .thenReturn(Optional.empty());
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(11L), eq(7L)))
                .thenReturn(Optional.empty());
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(eq(12L), eq(7L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recordService.updateRecordComment(7L, 10L, "comment"))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> recordService.updateRecordComment(7L, 11L, "comment"))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> recordService.updateRecordComment(7L, 12L, "comment"))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void rejectsNonPositiveRecordIdBeforeRepositoryLookup() {
        assertThatThrownBy(() -> recordService.updateRecordComment(7L, 0L, "comment"))
                .isInstanceOf(InvalidRecordIdException.class);
        assertThatThrownBy(() -> recordService.updateRecordComment(7L, -1L, "comment"))
                .isInstanceOf(InvalidRecordIdException.class);
        verifyNoInteractions(recordRepository);
    }

    private Record record(Long recordId) {
        Record record = mock(Record.class);
        when(record.getRecordId()).thenReturn(recordId);
        return record;
    }
}
