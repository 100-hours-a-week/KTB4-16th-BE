package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiClient;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiException;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordDeletionOrchestratorTest {
    @Mock
    private RecordService recordService;
    @Mock
    private RecordEmbeddingAiClient embeddingAiClient;

    @Test
    void callsAiDeleteAfterRecordServiceReturns() {
        RecordDeletionOrchestrator orchestrator = orchestrator();

        orchestrator.deleteRecord(7L, 125L);

        InOrder order = inOrder(recordService, embeddingAiClient);
        order.verify(recordService).deleteRecord(7L, 125L);
        order.verify(embeddingAiClient).delete(125L);
        verify(embeddingAiClient, times(1)).delete(125L);
    }

    @Test
    void keepsDeleteSuccessfulWhenAiClientThrowsRecordEmbeddingException() {
        doThrow(new RecordEmbeddingAiException(503))
                .when(embeddingAiClient).delete(125L);

        orchestrator().deleteRecord(7L, 125L);

        verify(recordService).deleteRecord(7L, 125L);
        verify(embeddingAiClient, times(1)).delete(125L);
    }

    @Test
    void keepsDeleteSuccessfulWhenAiClientThrowsUnexpectedRuntimeException() {
        doThrow(new IllegalStateException("sensitive transport detail"))
                .when(embeddingAiClient).delete(125L);

        orchestrator().deleteRecord(7L, 125L);

        verify(recordService).deleteRecord(7L, 125L);
        verify(embeddingAiClient, times(1)).delete(125L);
    }

    @Test
    void doesNotCallAiWhenRecordServiceFails() {
        doThrow(new RecordNotFoundException()).when(recordService).deleteRecord(7L, 125L);

        assertThatThrownBy(() -> orchestrator().deleteRecord(7L, 125L))
                .isInstanceOf(RecordNotFoundException.class);

        verifyNoInteractions(embeddingAiClient);
    }

    private RecordDeletionOrchestrator orchestrator() {
        return new RecordDeletionOrchestrator(recordService, embeddingAiClient);
    }
}
