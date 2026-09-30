package com.ktb4.team16.mulo.record.embedding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordEmbeddingSubmitterTest {
    private static final RecordEmbeddingSnapshot SNAPSHOT = new RecordEmbeddingSnapshot(
            125L,
            7L,
            "records/7/125.jpg",
            new RecordEmbeddingSnapshot.Track("title", "artist", "track-125"),
            "latest comment",
            LocalDateTime.parse("2026-09-30T19:40:00"));

    @Mock private GcsStorageService gcsStorageService;
    @Mock private RecordEmbeddingAiClient embeddingAiClient;

    @Test
    void signsForOneHourAndSubmitsAllSnapshotValues() {
        when(gcsStorageService.createReadSignedUrl("records/7/125.jpg", Duration.ofHours(1)))
                .thenReturn("https://signed.example/record-photo");

        submitter().submit(SNAPSHOT);

        ArgumentCaptor<EmbeddingGenerateRequest> request =
                ArgumentCaptor.forClass(EmbeddingGenerateRequest.class);
        verify(gcsStorageService).createReadSignedUrl(
                "records/7/125.jpg", Duration.ofHours(1));
        verify(embeddingAiClient).generate(request.capture());
        Assertions.assertThat(request.getValue()).isEqualTo(new EmbeddingGenerateRequest(
                125L,
                7L,
                "https://signed.example/record-photo",
                new EmbeddingGenerateRequest.Track("title", "artist", "track-125"),
                "latest comment",
                "2026-09-30T19:40:00+09:00"));
    }

    @Test
    void submitsNullCommentWithoutChangingOtherFields() {
        when(gcsStorageService.createReadSignedUrl(any(), any()))
                .thenReturn("https://signed.example/record-photo");
        RecordEmbeddingSnapshot nullComment = new RecordEmbeddingSnapshot(
                SNAPSHOT.recordId(), SNAPSHOT.userId(), SNAPSHOT.photoObjectKey(),
                SNAPSHOT.track(), null, SNAPSHOT.createdAt());

        submitter().submit(nullComment);

        ArgumentCaptor<EmbeddingGenerateRequest> request =
                ArgumentCaptor.forClass(EmbeddingGenerateRequest.class);
        verify(embeddingAiClient).generate(request.capture());
        Assertions.assertThat(request.getValue().comment()).isNull();
        Assertions.assertThat(request.getValue().recordId()).isEqualTo(SNAPSHOT.recordId());
        Assertions.assertThat(request.getValue().userId()).isEqualTo(SNAPSHOT.userId());
        Assertions.assertThat(request.getValue().track()).isEqualTo(
                new EmbeddingGenerateRequest.Track("title", "artist", "track-125"));
        Assertions.assertThat(request.getValue().createdAt())
                .isEqualTo("2026-09-30T19:40:00+09:00");
    }

    @Test
    void submitsEmptyCommentUnchanged() {
        when(gcsStorageService.createReadSignedUrl(any(), any()))
                .thenReturn("https://signed.example/record-photo");
        RecordEmbeddingSnapshot emptyComment = new RecordEmbeddingSnapshot(
                SNAPSHOT.recordId(), SNAPSHOT.userId(), SNAPSHOT.photoObjectKey(),
                SNAPSHOT.track(), "", SNAPSHOT.createdAt());

        submitter().submit(emptyComment);

        ArgumentCaptor<EmbeddingGenerateRequest> request =
                ArgumentCaptor.forClass(EmbeddingGenerateRequest.class);
        verify(embeddingAiClient).generate(request.capture());
        Assertions.assertThat(request.getValue().comment()).isEmpty();
    }

    @Test
    void isolatesAiFailureAfterCommentCommit() {
        when(gcsStorageService.createReadSignedUrl(any(), any()))
                .thenReturn("https://signed.example/record-photo");
        doThrow(new RecordEmbeddingAiException(503)).when(embeddingAiClient).generate(any());

        Assertions.assertThatCode(() -> submitter().submit(SNAPSHOT)).doesNotThrowAnyException();
    }

    @Test
    void isolatesSignedUrlFailureAndDoesNotCallAi() {
        when(gcsStorageService.createReadSignedUrl(any(), any()))
                .thenThrow(new IllegalStateException("signing unavailable"));

        Assertions.assertThatCode(() -> submitter().submit(SNAPSHOT)).doesNotThrowAnyException();
        verify(embeddingAiClient, never()).generate(any());
    }

    private RecordEmbeddingSubmitter submitter() {
        return new RecordEmbeddingSubmitter(gcsStorageService, embeddingAiClient);
    }
}
