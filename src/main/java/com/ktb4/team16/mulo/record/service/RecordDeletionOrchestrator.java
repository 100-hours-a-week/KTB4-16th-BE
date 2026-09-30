package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiClient;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordDeletionOrchestrator {
    private final RecordService recordService;
    private final RecordEmbeddingAiClient embeddingAiClient;

    public void deleteRecord(Long userId, Long recordId) {
        recordService.deleteRecord(userId, recordId);
        try {
            embeddingAiClient.delete(recordId);
        } catch (RecordEmbeddingAiException exception) {
            log.warn("Record embedding deletion failed after commit: recordId={}, failureType={}, "
                            + "httpStatus={}",
                    recordId, exception.failureType(), exception.httpStatus());
        } catch (RuntimeException exception) {
            log.warn("Record embedding deletion failed after commit: recordId={}, failureType={}",
                    recordId, exception.getClass().getSimpleName());
        }
    }
}
