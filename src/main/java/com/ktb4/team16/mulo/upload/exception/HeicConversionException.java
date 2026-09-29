package com.ktb4.team16.mulo.upload.exception;

public class HeicConversionException extends RuntimeException {
    private final Reason reason;

    public HeicConversionException(Reason reason) {
        this.reason = reason;
    }

    public HeicConversionException(Reason reason, Throwable cause) {
        super(cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_HEIC,
        HEIF_CONVERT_UNAVAILABLE,
        HEIF_CONVERT_FAILED,
        HEIF_CONVERT_TIMEOUT,
        JPEGTRAN_UNAVAILABLE,
        JPEGTRAN_FAILED,
        JPEGTRAN_TIMEOUT,
        INVALID_JPEG,
        PIXEL_LIMIT_EXCEEDED,
        OUTPUT_SIZE_EXCEEDED,
        IO_FAILURE,
        CAPACITY_TIMEOUT,
        INTERRUPTED
    }
}
