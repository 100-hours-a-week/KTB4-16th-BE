package com.ktb4.team16.mulo.global.error;

public record FieldError(String field, ErrorCode code, String message) {
    public static FieldError of(String field, ErrorCode code) {
        return new FieldError(field, code, code.message());
    }
}
