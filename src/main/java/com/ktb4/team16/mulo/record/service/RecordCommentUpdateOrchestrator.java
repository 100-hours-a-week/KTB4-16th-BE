package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordCommentUpdateOrchestrator {
    private final RecordService recordService;
    private final RecordEmbeddingSubmitter embeddingSubmitter;

    public RecordCommentUpdateResponse updateRecordComment(
            Long userId,
            Long recordId,
            String comment
    ) {
        RecordEmbeddingSnapshot snapshot = recordService.updateRecordComment(
                userId, recordId, comment);
        embeddingSubmitter.submit(snapshot);
        return new RecordCommentUpdateResponse(snapshot.recordId(), snapshot.comment());
    }
}
