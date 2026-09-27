package com.ktb4.team16.mulo.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.upload.dto.response.UploadResponse;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.exception.UploadNotFoundException;
import com.ktb4.team16.mulo.upload.repository.UploadRepository;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadServiceTest {
    private final UploadRepository uploadRepository = mock(UploadRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GcsStorageService gcsStorageService = mock(GcsStorageService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final UploadService uploadService = new UploadService(
            uploadRepository, userRepository, gcsStorageService, clock);
    private User user;

    @BeforeEach
    void setUp() {
        user = User.signup("test@example.com", "hash", "tester");
        when(userRepository.findByUserIdAndDeletedAtIsNull(35L)).thenReturn(Optional.of(user));
    }

    @Test
    void uploadsToGcsBeforeSavingMetadataAndReturnsUploadId() {
        Upload savedUpload = mock(Upload.class);
        when(savedUpload.getUploadId()).thenReturn(123L);
        when(uploadRepository.saveAndFlush(any(Upload.class))).thenReturn(savedUpload);

        UploadResponse response = uploadService.upload(35L, jpeg());

        assertThat(response.data().uploadId()).isEqualTo(123L);
        verify(gcsStorageService).upload(
                org.mockito.ArgumentMatchers.matches("uploads/35/.+\\.jpg"),
                any(byte[].class), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        verify(uploadRepository).saveAndFlush(any(Upload.class));
        verify(gcsStorageService, never()).delete(any());
    }

    @Test
    void deletesGcsObjectWhenMetadataSaveFails() {
        when(uploadRepository.saveAndFlush(any(Upload.class)))
                .thenThrow(new IllegalStateException("db failure"));

        assertThatThrownBy(() -> uploadService.upload(35L, jpeg()))
                .isInstanceOf(IllegalStateException.class);

        verify(gcsStorageService).delete(org.mockito.ArgumentMatchers.matches("uploads/35/.+\\.jpg"));
    }

    @Test
    void doesNotDeleteWhenGcsUploadFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("gcs failure"))
                .when(gcsStorageService).upload(any(), any(), any());

        assertThatThrownBy(() -> uploadService.upload(35L, jpeg()))
                .isInstanceOf(IllegalStateException.class);

        verify(uploadRepository, never()).saveAndFlush(any(Upload.class));
        verify(gcsStorageService, never()).delete(any());
    }

    @Test
    void keepsDatabaseFailureWhenCompensationDeleteAlsoFails() {
        IllegalStateException databaseFailure = new IllegalStateException("db failure");
        when(uploadRepository.saveAndFlush(any(Upload.class))).thenThrow(databaseFailure);
        org.mockito.Mockito.doThrow(new IllegalStateException("delete failure"))
                .when(gcsStorageService).delete(any());

        assertThatThrownBy(() -> uploadService.upload(35L, jpeg()))
                .isSameAs(databaseFailure);
        verify(gcsStorageService).delete(any());
    }

    @Test
    void createsReadSignedUrlOnlyAfterValidOwnerAndAgeLookup() {
        Upload upload = mock(Upload.class);
        when(upload.getImageUrl()).thenReturn("uploads/35/photo.jpg");
        when(uploadRepository.findByUploadIdAndUser_UserIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(123L),
                org.mockito.ArgumentMatchers.eq(35L),
                any())).thenReturn(Optional.of(upload));
        when(gcsStorageService.createReadSignedUrl("uploads/35/photo.jpg"))
                .thenReturn("https://signed.example/photo");

        assertThat(uploadService.createReadSignedUrl(35L, 123L))
                .isEqualTo("https://signed.example/photo");
        verify(gcsStorageService).createReadSignedUrl("uploads/35/photo.jpg");
        verify(uploadRepository, never()).save(any(Upload.class));

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(uploadRepository).findByUploadIdAndUser_UserIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(123L),
                org.mockito.ArgumentMatchers.eq(35L), cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 24, 8, 0));
    }

    @Test
    void rejectsSignedUrlForAnotherUsersUpload() {
        when(uploadRepository.findByUploadIdAndUser_UserIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(123L),
                org.mockito.ArgumentMatchers.eq(99L), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.createReadSignedUrl(99L, 123L))
                .isInstanceOf(UploadNotFoundException.class);
        verify(gcsStorageService, never()).createReadSignedUrl(any());
    }

    @Test
    void rejectsSignedUrlForExpiredUpload() {
        when(uploadRepository.findByUploadIdAndUser_UserIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(123L),
                org.mockito.ArgumentMatchers.eq(35L), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.createReadSignedUrl(35L, 123L))
                .isInstanceOf(UploadNotFoundException.class);
        verify(gcsStorageService, never()).createReadSignedUrl(any());
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("photo", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
    }
}
