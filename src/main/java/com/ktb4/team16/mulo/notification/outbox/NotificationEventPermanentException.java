package com.ktb4.team16.mulo.notification.outbox;

/** 자동 재시도해도 복구되지 않을 이벤트 처리 오류를 구분한다. */
public class NotificationEventPermanentException extends RuntimeException {

    private final String errorCode;

    /** payload 원문 대신 운영용 오류 코드를 기록한다. */
    public NotificationEventPermanentException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    /** Outbox last_error_code로 저장할 고정 코드를 반환한다. */
    public String errorCode() {
        return errorCode;
    }
}
