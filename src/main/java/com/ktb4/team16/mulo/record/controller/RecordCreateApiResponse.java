package com.ktb4.team16.mulo.record.controller;

import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;

public record RecordCreateApiResponse(
        String message,
        RecordCreateResponse data
) {
}
