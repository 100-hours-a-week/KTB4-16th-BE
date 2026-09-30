package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.embedding.EmbeddingGenerateRequest;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiClient;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiException;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordCreationOrchestratorTest {
    private static final RecordCreateRequest REQUEST = new RecordCreateRequest(
            new RecordCreateRequest.Location(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null, null),
            new RecordCreateRequest.Music("track-123", "밤편지", "아이유", "album", "external"),
            10, "비 냄새 좋았던 저녁", 11L);
    private static final RecordCreationSnapshot SNAPSHOT = new RecordCreationSnapshot(
            1024L, 7L, "records/7/photo.jpg",
            new RecordCreationSnapshot.Track("밤편지", "아이유", "track-123"),
            "비 냄새 좋았던 저녁", LocalDateTime.parse("2026-09-30T19:40:00"));

    @Mock private RecordService recordService;
    @Mock private GcsStorageService gcsStorageService;
    @Mock private RecordEmbeddingAiClient embeddingAiClient;

    @Test
    void callsAiAfterRecordServiceReturnsWithOneHourSignedUrlAndSnapshotPayload() {
        when(recordService.createRecord(7L, REQUEST)).thenReturn(SNAPSHOT);
        when(gcsStorageService.createReadSignedUrl("records/7/photo.jpg", Duration.ofHours(1)))
                .thenReturn("https://signed.example/photo");

        RecordCreateResponse response = orchestrator().createRecord(7L, REQUEST);

        assertThat(response).isEqualTo(new RecordCreateResponse(1024L));
        ArgumentCaptor<EmbeddingGenerateRequest> request =
                ArgumentCaptor.forClass(EmbeddingGenerateRequest.class);
        InOrder order = inOrder(recordService, gcsStorageService, embeddingAiClient);
        order.verify(recordService).createRecord(7L, REQUEST);
        order.verify(gcsStorageService).createReadSignedUrl(
                "records/7/photo.jpg", Duration.ofHours(1));
        order.verify(embeddingAiClient).generate(request.capture());
        assertThat(request.getValue().recordId()).isEqualTo(1024L);
        assertThat(request.getValue().userId()).isEqualTo(7L);
        assertThat(request.getValue().photoUrl()).isEqualTo("https://signed.example/photo");
        assertThat(request.getValue().track()).isEqualTo(
                new EmbeddingGenerateRequest.Track("밤편지", "아이유", "track-123"));
        assertThat(request.getValue().comment()).isEqualTo("비 냄새 좋았던 저녁");
        assertThat(request.getValue().createdAt()).isEqualTo("2026-09-30T19:40:00+09:00");
    }

    @Test
    void returnsSuccessfulCreateResponseWhenEmbeddingRequestFails() {
        when(recordService.createRecord(7L, REQUEST)).thenReturn(SNAPSHOT);
        when(gcsStorageService.createReadSignedUrl(any(), any()))
                .thenReturn("https://signed.example/photo");
        org.mockito.Mockito.doThrow(new RecordEmbeddingAiException(503))
                .when(embeddingAiClient).generate(any());

        assertThat(orchestrator().createRecord(7L, REQUEST))
                .isEqualTo(new RecordCreateResponse(1024L));
        verify(embeddingAiClient).generate(any());
    }

    @Test
    void doesNotCallGcsOrAiWhenRecordTransactionFails() {
        when(recordService.createRecord(eq(7L), eq(REQUEST)))
                .thenThrow(new IllegalStateException("commit failed"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(gcsStorageService, embeddingAiClient);
    }

    @Test
    void keepsSuccessfulCreateResponseWhenSigningUrlFails() {
        when(recordService.createRecord(7L, REQUEST)).thenReturn(SNAPSHOT);
        when(gcsStorageService.createReadSignedUrl(any(), any()))
                .thenThrow(new IllegalStateException("signing failed"));

        assertThat(orchestrator().createRecord(7L, REQUEST))
                .isEqualTo(new RecordCreateResponse(1024L));
        verify(embeddingAiClient, never()).generate(any());
    }

    private RecordCreationOrchestrator orchestrator() {
        return new RecordCreationOrchestrator(recordService, gcsStorageService, embeddingAiClient);
    }
}
