package com.ktb4.team16.mulo.record.controller;

import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;

public record RecordCommentUpdateApiResponse(
        String message,
        RecordCommentUpdateResponse data
) {
}
