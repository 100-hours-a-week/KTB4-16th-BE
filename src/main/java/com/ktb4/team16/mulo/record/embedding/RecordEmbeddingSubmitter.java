package com.ktb4.team16.mulo.record.embedding;

import com.ktb4.team16.mulo.upload.storage.GcsStorageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordEmbeddingSubmitter {
    private static final Duration EMBEDDING_PHOTO_URL_TTL = Duration.ofHours(1);

    private final GcsStorageService gcsStorageService;
    private final RecordEmbeddingAiClient embeddingAiClient;

    public void submit(RecordEmbeddingSnapshot snapshot) {
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
    }
}
