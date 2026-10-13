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
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
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
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecordCommentUpdateServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-09-30T19:40:00");
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
            mock(GcsStorageService.class)
    );

    @Test
    void updatesCommentAndCapturesEmbeddingDataForActiveOwnedRecord() {
        Record record = record(125L, "기존 코멘트");
        stubOwnedActiveRecord(record);

        RecordEmbeddingSnapshot snapshot = recordService.updateRecordComment(
                7L, 125L, "수정된 코멘트");

        assertThat(record.getComment()).isEqualTo("수정된 코멘트");
        assertThat(record.getUpdatedAt()).isNotNull();
        assertThat(snapshot).isEqualTo(new RecordEmbeddingSnapshot(
                125L,
                7L,
                "records/7/125.jpg",
                new RecordEmbeddingSnapshot.Track("title", "artist", "track-125"),
                "수정된 코멘트",
                CREATED_AT));
        verify(recordRepository).findByRecordIdAndUser_UserIdAndDeletedAtIsNull(125L, 7L);
        verify(recordPhotoRepository).findByRecord_RecordId(125L);
    }

    @Test
    void allowsNullCommentAndCapturesNullForEmbedding() {
        Record record = record(125L, "기존 코멘트");
        stubOwnedActiveRecord(record);

        RecordEmbeddingSnapshot snapshot = recordService.updateRecordComment(7L, 125L, null);

        assertThat(record.getComment()).isNull();
        assertThat(snapshot.comment()).isNull();
    }

    @Test
    void preservesEmptyCommentForEmbedding() {
        Record record = record(125L, "기존 코멘트");
        stubOwnedActiveRecord(record);

        RecordEmbeddingSnapshot snapshot = recordService.updateRecordComment(7L, 125L, "");

        assertThat(record.getComment()).isEmpty();
        assertThat(snapshot.comment()).isEmpty();
    }

    @Test
    void mapsMissingDeletedAndOtherUsersRecordsToNotFoundWithoutPhotoLookup() {
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
        when(recordPhotoRepository.findByRecord_RecordId(record.getRecordId()))
                .thenReturn(Optional.of(RecordPhoto.create(
                        record, "records/7/125.jpg", "image/jpeg", 123L)));
    }

    private Record record(Long recordId, String comment) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(7L);
        MusicTrack musicTrack = mock(MusicTrack.class);
        when(musicTrack.getTitle()).thenReturn("title");
        when(musicTrack.getArtistName()).thenReturn("artist");
        when(musicTrack.getExternalTrackId()).thenReturn("track-125");
        Record record = Record.create(user, mock(Place.class), musicTrack, null, null,
                (byte) 4, comment, CREATED_AT);
        ReflectionTestUtils.setField(record, "recordId", recordId);
        return record;
    }
}
