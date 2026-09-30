package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.embedding.EmbeddingGenerateRequest;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiClient;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingAiException;
import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordCreationOrchestrator {
    private static final Duration EMBEDDING_PHOTO_URL_TTL = Duration.ofHours(1);

    private final RecordService recordService;
    private final GcsStorageService gcsStorageService;
    private final RecordEmbeddingAiClient embeddingAiClient;

    public RecordCreateResponse createRecord(Long userId, RecordCreateRequest request) {
        RecordCreationSnapshot snapshot = recordService.createRecord(userId, request);
        try {
            String signedUrl = gcsStorageService.createReadSignedUrl(
                    snapshot.photoObjectKey(), EMBEDDING_PHOTO_URL_TTL);
            embeddingAiClient.generate(EmbeddingGenerateRequest.from(snapshot, signedUrl));
        } catch (RecordEmbeddingAiException exception) {
            log.warn("Record embedding request failed after commit: recordId={}, failureType={}, "
                            + "httpStatus={}",
                    snapshot.recordId(), exception.failureType(), exception.httpStatus());
        } catch (RuntimeException exception) {
            log.warn("Record embedding request failed after commit: recordId={}, failureType={}",
                    snapshot.recordId(), exception.getClass().getSimpleName());
        }
        return new RecordCreateResponse(snapshot.recordId());
    }
}
