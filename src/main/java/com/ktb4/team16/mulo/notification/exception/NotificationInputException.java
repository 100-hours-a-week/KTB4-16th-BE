package com.ktb4.team16.mulo.notification.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

/** 기존 알림 API의 입력 오류 필드와 세부 오류 코드를 보존한다. */
public class NotificationInputException extends RuntimeException {

    private final String fieldName;
    private final ErrorCode fieldErrorCode;

    /** 입력 필드별 검증 실패 정보를 예외로 전달한다. */
    public NotificationInputException(String fieldName, ErrorCode fieldErrorCode) {
        super(fieldErrorCode.message());
        this.fieldName = fieldName;
        this.fieldErrorCode = fieldErrorCode;
    }

    /** 오류 응답에서 문제가 된 입력 필드 이름을 반환한다. */
    public String fieldName() {
        return fieldName;
    }

    /** 오류 응답에서 문제가 된 입력의 상세 코드를 반환한다. */
    public ErrorCode fieldErrorCode() {
        return fieldErrorCode;
    }
}
