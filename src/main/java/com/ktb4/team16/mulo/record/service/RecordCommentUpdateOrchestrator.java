package com.ktb4.team16.mulo.record.service;

import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordCommentUpdateOrchestrator {
    private final RecordService recordService;

    public RecordCommentUpdateResponse updateRecordComment(
            Long userId,
            Long recordId,
            String comment
    ) {
        recordService.updateRecordComment(userId, recordId, comment);
        return new RecordCommentUpdateResponse(recordId, comment);
    }
}
