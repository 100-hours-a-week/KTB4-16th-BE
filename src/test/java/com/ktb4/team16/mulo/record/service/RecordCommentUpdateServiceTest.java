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
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.exception.InvalidRecordIdException;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.recordphoto.repository.RecordPhotoRepository;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecordCommentUpdateServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-09-30T19:40:00");
    private static final LocalDateTime FIXED_KST_TIME = LocalDateTime.parse("2026-10-16T00:04:05");
    private final RecordRepository recordRepository = mock(RecordRepository.class);
    private final RecordPhotoRepository recordPhotoRepository = mock(RecordPhotoRepository.class);
    private final RecordService recordService = new RecordService(
            recordRepository,
            mock(RecordCursorCodec.class),
            mock(UserRepository.class),
            mock(PlaceRepository.class),
            mock(UploadService.class),
            mock(MusicTrackService.class),
            recordPhotoRepository,
            mock(GcsStorageService.class),
            mock(com.ktb4.team16.mulo.recorddraft.repository.RecordDraftRepository.class),
            Clock.fixed(Instant.parse("2026-10-15T15:04:05Z"), ZoneId.of("Asia/Seoul"))
    );

    @Test
    void updatesCommentForActiveOwnedRecord() {
        Record record = record(125L, "기존 코멘트");
        stubOwnedActiveRecord(record);

        recordService.updateRecordComment(7L, 125L, "수정된 코멘트");

        assertThat(record.getComment()).isEqualTo("수정된 코멘트");
        assertThat(record.getUpdatedAt()).isEqualTo(FIXED_KST_TIME);
        verify(recordRepository).findByRecordIdAndUser_UserIdAndDeletedAtIsNull(125L, 7L);
        org.mockito.Mockito.verifyNoInteractions(recordPhotoRepository);
    }

    @Test
    void allowsNullComment() {
        Record record = record(125L, "기존 코멘트");
        stubOwnedActiveRecord(record);

        recordService.updateRecordComment(7L, 125L, null);

        assertThat(record.getComment()).isNull();
    }

    @Test
    void preservesEmptyComment() {
        Record record = record(125L, "기존 코멘트");
        stubOwnedActiveRecord(record);

        recordService.updateRecordComment(7L, 125L, "");

        assertThat(record.getComment()).isEmpty();
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
        verifyNoInteractions(recordPhotoRepository);
    }

    @Test
    void rejectsNonPositiveRecordIdBeforeRepositoryLookup() {
        assertThatThrownBy(() -> recordService.updateRecordComment(7L, 0L, "comment"))
                .isInstanceOf(InvalidRecordIdException.class);
        assertThatThrownBy(() -> recordService.updateRecordComment(7L, -1L, "comment"))
                .isInstanceOf(InvalidRecordIdException.class);
        verifyNoInteractions(recordRepository, recordPhotoRepository);
    }

    private void stubOwnedActiveRecord(Record record) {
        when(recordRepository.findByRecordIdAndUser_UserIdAndDeletedAtIsNull(
                record.getRecordId(), 7L)).thenReturn(Optional.of(record));
    }

    private Record record(Long recordId, String comment) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(7L);
        Record record = Record.create(user, mock(com.ktb4.team16.mulo.place.entity.Place.class),
                mock(com.ktb4.team16.mulo.music.entity.MusicTrack.class), null, null,
                (byte) 4, comment, CREATED_AT);
        ReflectionTestUtils.setField(record, "recordId", recordId);
        return record;
    }
}
