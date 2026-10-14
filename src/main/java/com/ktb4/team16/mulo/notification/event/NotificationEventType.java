package com.ktb4.team16.mulo.notification.event;

/** Outbox에 저장할 수 있는 도메인 이벤트 종류를 고정한다. */
public enum NotificationEventType {
    MONTHLY_REPORT_CREATED,
    MATCH_COMPLETED,
    FRIEND_REQUEST,
    FRIEND_ACCEPTED
}
