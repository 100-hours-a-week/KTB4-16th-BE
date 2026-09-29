package com.ktb4.team16.mulo.upload.dto.response;

public record UploadSignedUrlResponse(String message, Data data) {
    public record Data(String signedUrl) { }
}
