package com.ktb4.team16.mulo.record.embedding;

public class RecordEmbeddingAiException extends RuntimeException {
    private final Integer httpStatus;
    private final FailureType failureType;

    public RecordEmbeddingAiException() {
        this(null, FailureType.CLIENT_ERROR);
    }

    public RecordEmbeddingAiException(Integer httpStatus) {
        this(httpStatus, FailureType.HTTP_RESPONSE);
    }

    public RecordEmbeddingAiException(Integer httpStatus, FailureType failureType) {
        super("Record embedding request failed");
        this.httpStatus = httpStatus;
        this.failureType = failureType;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public FailureType failureType() {
        return failureType;
    }

    public enum FailureType {
        HTTP_RESPONSE,
        INVALID_RESPONSE,
        CLIENT_ERROR
    }
}
