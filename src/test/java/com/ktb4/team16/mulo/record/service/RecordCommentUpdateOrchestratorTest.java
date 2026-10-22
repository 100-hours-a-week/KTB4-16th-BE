package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import com.ktb4.team16.mulo.record.exception.RecordNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordCommentUpdateOrchestratorTest {
    @Mock private RecordService recordService;

    @Test
    void updatesCommentAndKeepsPatchResponse() {
        RecordCommentUpdateResponse response = orchestrator()
                .updateRecordComment(7L, 125L, "latest comment");

        assertThat(response).isEqualTo(new RecordCommentUpdateResponse(125L, "latest comment"));
        verify(recordService).updateRecordComment(7L, 125L, "latest comment");
    }

    @Test
    void returnsNullCommentResponse() {
        assertThat(orchestrator().updateRecordComment(7L, 125L, null))
                .isEqualTo(new RecordCommentUpdateResponse(125L, null));
        verify(recordService).updateRecordComment(7L, 125L, null);
    }

    @Test
    void returnsEmptyCommentResponse() {
        assertThat(orchestrator().updateRecordComment(7L, 125L, ""))
                .isEqualTo(new RecordCommentUpdateResponse(125L, ""));
        verify(recordService).updateRecordComment(7L, 125L, "");
    }

    @Test
    void propagatesExistingNotFoundFlow() {
        org.mockito.Mockito.doThrow(new RecordNotFoundException())
                .when(recordService).updateRecordComment(7L, 125L, "latest comment");

        assertThatThrownBy(() -> orchestrator()
                .updateRecordComment(7L, 125L, "latest comment"))
                .isInstanceOf(RecordNotFoundException.class);
    }

    private RecordCommentUpdateOrchestrator orchestrator() {
        return new RecordCommentUpdateOrchestrator(recordService);
    }
}
