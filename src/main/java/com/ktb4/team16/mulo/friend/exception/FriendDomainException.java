package com.ktb4.team16.mulo.friend.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

/** 친구 도메인 오류를 기존 API 오류 코드 체계로 전달한다. */
public class FriendDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 도메인 오류 코드를 예외 메시지와 함께 보관한다. */
    public FriendDomainException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /** HTTP 예외 응답에 사용할 계약 오류 코드를 반환한다. */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
