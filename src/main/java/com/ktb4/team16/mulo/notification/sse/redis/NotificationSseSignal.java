package com.ktb4.team16.mulo.notification.sse.redis;

public record NotificationSseSignal(int version, long userId) {

    public static final int CURRENT_VERSION = 1;

    public NotificationSseSignal {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 SSE 알림 신호 버전입니다.");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("SSE 알림 신호의 수신자 ID가 올바르지 않습니다.");
        }
    }

    public static NotificationSseSignal forRecipient(long userId) {
        return new NotificationSseSignal(CURRENT_VERSION, userId);
    }
}
