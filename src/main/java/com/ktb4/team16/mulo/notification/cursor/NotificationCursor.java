package com.ktb4.team16.mulo.notification.cursor;

import java.time.LocalDateTime;

/** 알림 목록의 최신순 keyset 경계를 표현한다. */
public record NotificationCursor(LocalDateTime createdAt, Long notificationId) {
}
