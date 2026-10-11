package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordCreationOrchestrator {
    private static final Set<String> RETRYABLE_UNIQUE_CONSTRAINTS = Set.of(
            "uk_places_coordinates",
            "uk_music_tracks_external_track_id");

    private final RecordCreationPreparationService preparationService;
    private final RecordService recordService;
    private final RecordEmbeddingSubmitter embeddingSubmitter;

    public RecordCreateResponse createRecord(Long userId, RecordCreateRequest request) {
        var preparedRegion = preparationService.preparePlace(request);
        PreparedWeather preparedWeather = preparationService.prepareWeather(request);
        RecordEmbeddingSnapshot snapshot = createRecordWithUniqueConflictRetry(
                userId, request, preparedRegion, preparedWeather);
        embeddingSubmitter.submit(snapshot);
        return new RecordCreateResponse(snapshot.recordId());
    }

    private RecordEmbeddingSnapshot createRecordWithUniqueConflictRetry(
            Long userId,
            RecordCreateRequest request,
            KakaoRegionClient.LegalRegion preparedRegion,
            PreparedWeather preparedWeather) {
        try {
            return recordService.createRecord(userId, request, preparedRegion, preparedWeather);
        } catch (RuntimeException exception) {
            if (!isRetryableUniqueConflict(exception)) {
                throw exception;
            }
        }

        // RecordService is a separate proxied bean. Its transaction has rolled back before
        // control reaches here, so this call starts a fresh transaction.
        return recordService.createRecord(userId, request, preparedRegion, preparedWeather);
    }

    private boolean isRetryableUniqueConflict(Throwable failure) {
        boolean integrityViolation = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataIntegrityViolationException) {
                integrityViolation = true;
            }
            if (integrityViolation && cause instanceof ConstraintViolationException violation
                    && RETRYABLE_UNIQUE_CONSTRAINTS.contains(
                            unqualifiedConstraintName(violation.getConstraintName()))) {
                return true;
            }
        }
        return false;
    }

    private String unqualifiedConstraintName(String constraintName) {
        if (constraintName == null) {
            return "";
        }
        int separator = constraintName.lastIndexOf('.');
        String unqualified = separator >= 0
                ? constraintName.substring(separator + 1)
                : constraintName;
        return unqualified.replace("`", "").replace("\"", "");
    }
}
