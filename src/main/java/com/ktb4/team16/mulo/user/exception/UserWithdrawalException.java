package com.ktb4.team16.mulo.user.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

public class UserWithdrawalException extends RuntimeException {
    private final ErrorCode errorCode;

    /** 탈퇴 비밀번호 오류를 기존 API 오류 코드로 전달한다. */
    public UserWithdrawalException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    /** 응답 계층이 사용할 오류 코드를 반환한다. */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
