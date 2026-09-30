package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordCreationOrchestratorTest {
    private static final RecordCreateRequest REQUEST = new RecordCreateRequest(
            new RecordCreateRequest.Location(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null, null),
            new RecordCreateRequest.Music("track-123", "밤편지", "아이유", "album", "external"),
            10, "비 냄새 좋았던 저녁", 11L);
    private static final RecordEmbeddingSnapshot SNAPSHOT = new RecordEmbeddingSnapshot(
            1024L, 7L, "records/7/photo.jpg",
            new RecordEmbeddingSnapshot.Track("밤편지", "아이유", "track-123"),
            "비 냄새 좋았던 저녁", LocalDateTime.parse("2026-09-30T19:40:00"));

    @Mock private RecordService recordService;
    @Mock private RecordEmbeddingSubmitter embeddingSubmitter;

    @Test
    void submitsEmbeddingAfterRecordServiceReturnsAndKeepsCreateResponse() {
        when(recordService.createRecord(7L, REQUEST)).thenReturn(SNAPSHOT);

        RecordCreateResponse response = orchestrator().createRecord(7L, REQUEST);

        assertThat(response).isEqualTo(new RecordCreateResponse(1024L));
        InOrder order = inOrder(recordService, embeddingSubmitter);
        order.verify(recordService).createRecord(7L, REQUEST);
        order.verify(embeddingSubmitter).submit(SNAPSHOT);
    }

    @Test
    void doesNotSubmitEmbeddingWhenRecordTransactionFails() {
        when(recordService.createRecord(eq(7L), eq(REQUEST)))
                .thenThrow(new IllegalStateException("commit failed"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> orchestrator().createRecord(7L, REQUEST)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(embeddingSubmitter);
    }

    private RecordCreationOrchestrator orchestrator() {
        return new RecordCreationOrchestrator(recordService, embeddingSubmitter);
    }
}
