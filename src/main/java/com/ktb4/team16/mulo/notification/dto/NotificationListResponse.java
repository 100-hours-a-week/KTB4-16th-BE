package com.ktb4.team16.mulo.notification.dto;

import com.ktb4.team16.mulo.notification.entity.Notification;
import com.ktb4.team16.mulo.notification.entity.NotificationType;
import java.time.LocalDateTime;
import java.util.List;

/** 알림 목록을 OpenAPI의 message/data 응답으로 반환한다. */
public record NotificationListResponse(String message, Data data) {

    /** 알림 행과 다음 페이지 cursor를 묶는다. */
    public record Data(List<Item> notifications, String nextCursor) {
    }

    /** 사용자 화면이 대상 ID가 사라진 과거 알림도 표시할 수 있게 한다. */
    public record Item(
            Long notificationId,
            NotificationType type,
            Long targetId,
            String targetType,
            String title,
            String content,
            LocalDateTime createdAt,
            LocalDateTime readAt
    ) {
        /** 영속 알림에서 응답에 필요한 값만 추출한다. */
        public static Item from(Notification notification) {
            return new Item(notification.getNotificationId(), notification.getType(),
                    notification.targetId(), notification.targetType(), notification.getTitle(),
                    notification.getContent(), notification.getCreatedAt(), notification.getReadAt());
        }
    }
}
