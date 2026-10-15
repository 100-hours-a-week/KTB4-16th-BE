package com.ktb4.team16.mulo.recorddraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.recorddraft.dto.request.RecordDraftUpsertRequest;
import com.ktb4.team16.mulo.recorddraft.entity.RecordDraft;
import com.ktb4.team16.mulo.recorddraft.repository.RecordDraftRepository;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.service.UploadService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecordDraftServiceTest {
    private static final Long USER_ID = 17L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 9, 0);

    private final RecordDraftRepository repository = mock(RecordDraftRepository.class);
    private final UploadService uploadService = mock(UploadService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final User user = mock(User.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final RecordDraftService service = new RecordDraftService(
            repository, uploadService, userRepository, clock);

    @BeforeEach
    void setUp() {
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(USER_ID))
                .thenReturn(Optional.of(user));
        when(repository.saveAndFlush(any(RecordDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsDraftAndDefaultsMoodScoreToZero() {
        when(repository.findByUser_UserId(USER_ID)).thenReturn(Optional.empty());
        RecordDraftUpsertRequest request = new RecordDraftUpsertRequest(
                new RecordDraftUpsertRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"), "code", "name"),
                new RecordDraftUpsertRequest.Music("track", "title", "artist", "album", "url"),
                null, "note", null);

        service.upsertRecordDraft(USER_ID, request);

        ArgumentCaptor<RecordDraft> captor = ArgumentCaptor.forClass(RecordDraft.class);
        verify(repository).saveAndFlush(captor.capture());
        RecordDraft draft = captor.getValue();
        assertThat(draft.getUser()).isSameAs(user);
        assertThat(draft.getLatitude()).isEqualByComparingTo("37.5");
        assertThat(draft.getExternalTrackId()).isEqualTo("track");
        assertThat(draft.getMoodScore()).isZero();
        assertThat(draft.getComment()).isEqualTo("note");
        assertThat(draft.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void replacesAllFieldsAndClearsOmittedValues() {
        RecordDraft existing = draft(NOW.minusDays(1), null);
        when(repository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(existing));

        service.upsertRecordDraft(USER_ID,
                new RecordDraftUpsertRequest(null, null, null, null, null));

        assertThat(existing.getLatitude()).isNull();
        assertThat(existing.getExternalTrackId()).isNull();
        assertThat(existing.getMoodScore()).isZero();
        assertThat(existing.getComment()).isNull();
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void refreshesExpiredDraftWithFullSnapshotAndPreservesCreatedAt() {
        LocalDateTime expiredAt = NOW.minusDays(15);
        RecordDraft expired = draft(expiredAt, null);
        when(repository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(expired));
        RecordDraftUpsertRequest request = new RecordDraftUpsertRequest(
                null,
                new RecordDraftUpsertRequest.Music("new-track", "new title", "new artist",
                        "new album", "new url"),
                7,
                "refreshed",
                null);

        service.upsertRecordDraft(USER_ID, request);

        assertThat(expired.getCreatedAt()).isEqualTo(expiredAt);
        assertThat(expired.getUpdatedAt()).isEqualTo(NOW);
        assertThat(expired.getExternalTrackId()).isEqualTo("new-track");
        assertThat(expired.getTitle()).isEqualTo("new title");
        assertThat(expired.getLatitude()).isNull();
        assertThat(expired.getMoodScore()).isEqualTo((byte) 7);
        assertThat(expired.getComment()).isEqualTo("refreshed");
        assertThat(service.findActiveRecordDraftForOwner(USER_ID)).contains(expired);
        verify(repository).saveAndFlush(expired);
    }

    @Test
    void usesAValidNewUploadAndKeepsAnAlreadyAttachedDraftUploadAfterOneHour() {
        Upload upload = mock(Upload.class);
        when(upload.getUploadId()).thenReturn(88L);
        RecordDraft existing = draft(NOW.minusDays(1), upload);
        when(repository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(existing));
        when(uploadService.findOwnedUploadForDraft(USER_ID, 88L)).thenReturn(upload);

        service.upsertRecordDraft(USER_ID,
                new RecordDraftUpsertRequest(null, null, 0, null, 88L));

        verify(uploadService).findOwnedUploadForDraft(USER_ID, 88L);
        verify(uploadService, org.mockito.Mockito.never()).findValidUpload(USER_ID, 88L);
    }

    @Test
    void doesNotAllowAnExpiredDraftUploadToBypassTheOneHourRule() {
        Upload oldUpload = mock(Upload.class);
        when(oldUpload.getUploadId()).thenReturn(88L);
        when(repository.findByUser_UserId(USER_ID))
                .thenReturn(Optional.of(draft(NOW.minusDays(14), oldUpload)));
        when(uploadService.findValidUpload(USER_ID, 88L))
                .thenThrow(new IllegalArgumentException("expired"));

        assertThatThrownBy(() -> service.upsertRecordDraft(USER_ID,
                new RecordDraftUpsertRequest(null, null, null, null, 88L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(uploadService).findValidUpload(USER_ID, 88L);
    }

    @Test
    void validatesDifferentUploadWithTheNormalOneHourPolicy() {
        Upload previousUpload = mock(Upload.class);
        Upload nextUpload = mock(Upload.class);
        when(previousUpload.getUploadId()).thenReturn(88L);
        when(repository.findByUser_UserId(USER_ID))
                .thenReturn(Optional.of(draft(NOW.minusDays(1), previousUpload)));
        when(uploadService.findValidUpload(USER_ID, 99L)).thenReturn(nextUpload);

        service.upsertRecordDraft(USER_ID,
                new RecordDraftUpsertRequest(null, null, 0, null, 99L));

        verify(uploadService).findValidUpload(USER_ID, 99L);
    }

    @Test
    void rejectsInactiveUserBeforeSaving() {
        when(userRepository.findByUserIdAndDeletedAtIsNullForUpdate(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertRecordDraft(USER_ID,
                new RecordDraftUpsertRequest(null, null, null, null, null)))
                .isInstanceOf(UnauthenticatedUserException.class);
    }

    @Test
    void excludesDraftAtExactExpirationBoundaryWithoutChangingUpdatedAt() {
        RecordDraft expired = draft(NOW.minusDays(14), null);
        when(repository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(expired));

        assertThat(service.findActiveRecordDraftForOwner(USER_ID)).isEmpty();
        assertThat(expired.getUpdatedAt()).isEqualTo(NOW.minusDays(14));
    }

    @Test
    void returnsActiveDraftBeforeExpirationWithoutUpdatingIt() {
        RecordDraft active = draft(NOW.minusDays(14).plusNanos(1), null);
        when(repository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(active));

        assertThat(service.findActiveRecordDraftForOwner(USER_ID)).contains(active);
        assertThat(active.getUpdatedAt()).isEqualTo(NOW.minusDays(14).plusNanos(1));
    }

    @Test
    void issuesNewSignedUrlForDraftUploadWithoutApplyingOneHourLimit() {
        Upload upload = mock(Upload.class);
        when(upload.getUploadId()).thenReturn(88L);
        when(uploadService.createDraftReadSignedUrl(USER_ID, 88L)).thenReturn("signed-url");
        RecordDraft draft = draft(NOW.minusDays(2), upload);

        assertThat(service.createPhotoUrl(USER_ID, draft)).isEqualTo("signed-url");
    }

    @Test
    void deletesDraftIdempotentlyByOwner() {
        service.deleteRecordDraft(USER_ID);

        verify(repository).deleteByUser_UserId(USER_ID);
        org.mockito.Mockito.verifyNoInteractions(uploadService);
    }

    private RecordDraft draft(LocalDateTime updatedAt, Upload upload) {
        return RecordDraft.create(user, new BigDecimal("37.5"), new BigDecimal("127.0"),
                "code", "name", "track", "title", "artist", "album", "url",
                (byte) 5, "note", upload, updatedAt);
    }
}
