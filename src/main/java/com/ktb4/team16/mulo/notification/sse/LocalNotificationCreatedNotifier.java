package com.ktb4.team16.mulo.notification.sse;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class LocalNotificationCreatedNotifier implements NotificationCreatedNotifier {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationCreatedNotifier.class);

    private final NotificationSseConnectionManager connections;
    private final ThreadPoolTaskExecutor executor;

    public LocalNotificationCreatedNotifier(NotificationSseConnectionManager connections,
            @Qualifier("notificationSseDeliveryExecutor") ThreadPoolTaskExecutor executor) {
        this.connections = connections;
        this.executor = executor;
    }

    @Override
    public void notificationCreated(Set<Long> recipientUserIds) {
        for (Long recipientUserId : recipientUserIds) {
            try {
                executor.execute(() -> {
                    try {
                        connections.sendNotificationCreated(recipientUserId);
                    } catch (RuntimeException exception) {
                        log.warn("SSE 알림 신호 전송 중 오류가 발생했습니다: failureType={}",
                                exception.getClass().getSimpleName());
                        closeForResynchronization(recipientUserId);
                    }
                });
            } catch (RuntimeException exception) {
                log.warn("SSE 알림 신호 작업을 제출하지 못했습니다: failureType={}",
                        exception.getClass().getSimpleName());
                closeForResynchronization(recipientUserId);
            }
        }
    }

    private void closeForResynchronization(Long recipientUserId) {
        try {
            connections.closeForResynchronization(recipientUserId);
        } catch (RuntimeException exception) {
            log.warn("SSE 연결을 재동기화 목적으로 종료하지 못했습니다: failureType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
