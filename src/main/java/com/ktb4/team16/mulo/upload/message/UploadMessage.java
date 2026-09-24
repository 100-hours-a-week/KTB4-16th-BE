package com.ktb4.team16.mulo.upload.message;

public enum UploadMessage {
    UPLOAD_COMPLETED("사진 업로드 성공");

    private final String message;

    UploadMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
