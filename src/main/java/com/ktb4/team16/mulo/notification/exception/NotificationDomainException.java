package com.ktb4.team16.mulo.notification.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

/** 알림 소유권 또는 요청 상태 오류를 전역 API 오류로 전달한다. */
public class NotificationDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 알림 기능에서 선택한 표준 HTTP 오류 코드를 보존한다. */
    public NotificationDomainException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /** 예외 핸들러가 HTTP 상태와 응답 코드를 읽을 수 있게 한다. */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
