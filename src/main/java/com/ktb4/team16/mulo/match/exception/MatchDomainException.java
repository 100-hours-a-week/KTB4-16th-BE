package com.ktb4.team16.mulo.match.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

public class MatchDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public MatchDomainException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public MatchDomainException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
