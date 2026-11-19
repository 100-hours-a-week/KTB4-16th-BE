package com.ktb4.team16.mulo.notification.sse.redis;

import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import java.util.Objects;
import java.util.Set;

public final class NotificationSseRedisSubscriber implements AutoCloseable {

    private final NotificationSseRedisTransport transport;
    private final LocalNotificationCreatedNotifier localNotifier;

    private boolean started;
    private boolean closed;

    public NotificationSseRedisSubscriber(NotificationSseRedisTransport transport,
            LocalNotificationCreatedNotifier localNotifier) {
        this.transport = Objects.requireNonNull(transport);
        this.localNotifier = Objects.requireNonNull(localNotifier);
    }

    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("종료된 SSE 알림 Redis 구독은 다시 시작할 수 없습니다.");
        }
        if (started) {
            return;
        }
        transport.subscribe(signal -> localNotifier.notificationCreated(Set.of(signal.userId())));
        started = true;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (started) {
            started = false;
            transport.unsubscribe();
        }
    }
}
