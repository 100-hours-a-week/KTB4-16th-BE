package com.ktb4.team16.mulo.notification.sse.redis;

import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class RedisNotificationCreatedNotifier implements NotificationCreatedNotifier {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationCreatedNotifier.class);

    private final NotificationSseRedisPublisher publisher;
    private final ThreadPoolTaskExecutor executor;
    private final NotificationSseConnectionManager connections;

    public RedisNotificationCreatedNotifier(NotificationSseRedisPublisher publisher,
            ThreadPoolTaskExecutor executor, NotificationSseConnectionManager connections) {
        this.publisher = publisher;
        this.executor = executor;
        this.connections = connections;
    }

    @Override
    public void notificationCreated(Set<Long> recipientUserIds) {
        for (Long recipientUserId : recipientUserIds) {
            try {
                executor.execute(() -> {
                    try {
                        publisher.publish(recipientUserId);
                    } catch (RuntimeException exception) {
                        log.warn("SSE 알림 Redis 신호를 발행하지 못했습니다: failureType={}",
                                exception.getClass().getSimpleName());
                        closeForResynchronization(recipientUserId);
                    }
                });
            } catch (RuntimeException exception) {
                log.warn("SSE 알림 Redis 발행 작업을 제출하지 못했습니다: failureType={}",
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
