package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordCommentUpdateOrchestratorTest {
    private static final RecordEmbeddingSnapshot SNAPSHOT = new RecordEmbeddingSnapshot(
            125L, 7L, "records/7/125.jpg",
            new RecordEmbeddingSnapshot.Track("title", "artist", "track-125"),
            "latest comment", LocalDateTime.parse("2026-09-30T19:40:00"));

    @Mock private RecordService recordService;
    @Mock private RecordEmbeddingSubmitter embeddingSubmitter;

    @Test
    void submitsEmbeddingAfterUpdateServiceReturnsAndKeepsPatchResponse() {
        when(recordService.updateRecordComment(7L, 125L, "latest comment"))
                .thenReturn(SNAPSHOT);

        RecordCommentUpdateResponse response = orchestrator()
                .updateRecordComment(7L, 125L, "latest comment");

        assertThat(response).isEqualTo(new RecordCommentUpdateResponse(125L, "latest comment"));
        InOrder order = inOrder(recordService, embeddingSubmitter);
        order.verify(recordService).updateRecordComment(7L, 125L, "latest comment");
        order.verify(embeddingSubmitter).submit(SNAPSHOT);
    }

    @Test
    void returnsNullCommentResponseAndSubmitsNullSnapshotComment() {
        RecordEmbeddingSnapshot nullComment = new RecordEmbeddingSnapshot(
                SNAPSHOT.recordId(), SNAPSHOT.userId(), SNAPSHOT.photoObjectKey(),
                SNAPSHOT.track(), null, SNAPSHOT.createdAt());
        when(recordService.updateRecordComment(7L, 125L, null)).thenReturn(nullComment);

        assertThat(orchestrator().updateRecordComment(7L, 125L, null))
                .isEqualTo(new RecordCommentUpdateResponse(125L, null));
        verify(embeddingSubmitter).submit(nullComment);
    }

    @Test
    void returnsEmptyCommentResponseAndSubmitsEmptySnapshotComment() {
        RecordEmbeddingSnapshot emptyComment = new RecordEmbeddingSnapshot(
                SNAPSHOT.recordId(), SNAPSHOT.userId(), SNAPSHOT.photoObjectKey(),
                SNAPSHOT.track(), "", SNAPSHOT.createdAt());
        when(recordService.updateRecordComment(7L, 125L, "")).thenReturn(emptyComment);

        assertThat(orchestrator().updateRecordComment(7L, 125L, ""))
                .isEqualTo(new RecordCommentUpdateResponse(125L, ""));
        verify(embeddingSubmitter).submit(emptyComment);
    }

    @Test
    void doesNotSubmitEmbeddingWhenUpdateTransactionFails() {
        when(recordService.updateRecordComment(7L, 125L, "latest comment"))
                .thenThrow(new IllegalStateException("commit failed"));

        assertThatThrownBy(() -> orchestrator()
                .updateRecordComment(7L, 125L, "latest comment"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(embeddingSubmitter);
    }

    @Test
    void doesNotSubmitEmbeddingForExistingNotFoundFlow() {
        when(recordService.updateRecordComment(7L, 125L, "latest comment"))
                .thenThrow(new RecordNotFoundException());

        assertThatThrownBy(() -> orchestrator()
                .updateRecordComment(7L, 125L, "latest comment"))
                .isInstanceOf(RecordNotFoundException.class);
        verifyNoInteractions(embeddingSubmitter);
    }

    private RecordCommentUpdateOrchestrator orchestrator() {
        return new RecordCommentUpdateOrchestrator(recordService, embeddingSubmitter);
    }
}
