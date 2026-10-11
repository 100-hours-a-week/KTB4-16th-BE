package com.ktb4.team16.mulo.notification.outbox;

import com.ktb4.team16.mulo.notification.entity.NotificationType;

/** 검증된 이벤트에서 만들어지는 수신자별 알림 한 건을 표현한다. */
public record NotificationDraft(
        Long recipientUserId,
        NotificationType type,
        Long targetId,
        String title,
        String content
) {
}
