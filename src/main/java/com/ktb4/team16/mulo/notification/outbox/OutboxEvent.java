package com.ktb4.team16.mulo.notification.outbox;

import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import java.time.LocalDateTime;
import java.util.UUID;

/** 잠금 조회한 Outbox 행에서 worker 처리에 필요한 스냅샷을 보유한다. */
public record OutboxEvent(
        UUID eventId,
        NotificationEventType eventType,
        Long aggregateId,
        String payload,
        int retryCount,
        LocalDateTime nextRetryAt
) {
}
