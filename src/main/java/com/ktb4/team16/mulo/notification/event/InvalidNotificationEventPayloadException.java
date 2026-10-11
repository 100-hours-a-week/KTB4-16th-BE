package com.ktb4.team16.mulo.notification.event;

/** 재시도로 복구되지 않는 알림 이벤트 payload 오류를 표시한다. */
public class InvalidNotificationEventPayloadException extends RuntimeException {

    /** 원본 payload를 로그에 노출하지 않고 일반 원인 코드만 전달한다. */
    public InvalidNotificationEventPayloadException(String reason) {
        super(reason);
    }

    /** JSON 파싱 오류를 영구 payload 오류로 변환한다. */
    public InvalidNotificationEventPayloadException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
