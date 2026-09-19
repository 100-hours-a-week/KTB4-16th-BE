package com.ktb4.team16.mulo.user.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

public class PasswordChangeException extends RuntimeException {
    private final ErrorCode errorCode;

    public PasswordChangeException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
