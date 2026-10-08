package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordCreationOrchestrator {
    private final RecordCreationPreparationService preparationService;
    private final RecordService recordService;
    private final RecordEmbeddingSubmitter embeddingSubmitter;

    public RecordCreateResponse createRecord(Long userId, RecordCreateRequest request) {
        var preparedRegion = preparationService.preparePlace(request);
        PreparedWeather preparedWeather = preparationService.prepareWeather(request);
        RecordEmbeddingSnapshot snapshot = recordService.createRecord(
                userId, request, preparedRegion, preparedWeather);
        embeddingSubmitter.submit(snapshot);
        return new RecordCreateResponse(snapshot.recordId());
    }
}
