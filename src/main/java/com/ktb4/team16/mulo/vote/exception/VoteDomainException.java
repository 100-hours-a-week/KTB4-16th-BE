package com.ktb4.team16.mulo.vote.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

public class VoteDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public VoteDomainException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
