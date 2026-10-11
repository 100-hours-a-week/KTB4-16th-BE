package com.ktb4.team16.mulo.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** PATCH batch read 요청의 필수 알림 ID 목록을 검증한다. */
public record NotificationReadRequest(
        @NotEmpty(message = "NOTIFICATION_IDS_REQUIRED")
        List<@NotNull(message = "INVALID_NOTIFICATION_ID")
                @Positive(message = "INVALID_NOTIFICATION_ID") Long> notificationIds
) {
}
