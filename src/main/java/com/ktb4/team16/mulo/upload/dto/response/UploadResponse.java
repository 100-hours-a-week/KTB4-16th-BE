package com.ktb4.team16.mulo.upload.dto.response;

public record UploadResponse(String message, Data data) {
    public record Data(Long uploadId) {
    }
}
