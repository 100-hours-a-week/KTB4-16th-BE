package com.ktb4.team16.mulo.notification.sse.redis;

import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationSseRedisSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseRedisSubscriber.class);

    private final NotificationSseRedisTransport transport;
    private final LocalNotificationCreatedNotifier localNotifier;
    private final NotificationSseConnectionManager connections;
    private final Duration recoveryCheckInterval;
    private final ScheduledExecutorService monitor;

    private boolean started;
    private boolean closed;
    private boolean available;
    private boolean failureLogged;

    public NotificationSseRedisSubscriber(NotificationSseRedisTransport transport,
            LocalNotificationCreatedNotifier localNotifier, NotificationSseConnectionManager connections,
            Duration recoveryCheckInterval) {
        this(transport, localNotifier, connections, recoveryCheckInterval,
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "notification-sse-redis-recovery");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    NotificationSseRedisSubscriber(NotificationSseRedisTransport transport,
            LocalNotificationCreatedNotifier localNotifier, NotificationSseConnectionManager connections,
            Duration recoveryCheckInterval, ScheduledExecutorService monitor) {
        this.transport = Objects.requireNonNull(transport);
        this.localNotifier = Objects.requireNonNull(localNotifier);
        this.connections = Objects.requireNonNull(connections);
        this.monitor = Objects.requireNonNull(monitor);
        if (recoveryCheckInterval == null || recoveryCheckInterval.isNegative()
                || recoveryCheckInterval.isZero() || recoveryCheckInterval.toMillis() == 0) {
            throw new IllegalArgumentException("SSE 알림 Redis 복구 확인 주기는 양수여야 합니다.");
        }
        this.recoveryCheckInterval = recoveryCheckInterval;
    }

    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("종료된 SSE 알림 Redis 구독은 다시 시작할 수 없습니다.");
        }
        if (started) {
            return;
        }
        started = true;
        connections.setDeliveryAvailable(false);
        recover();
        monitor.scheduleWithFixedDelay(this::recover, recoveryCheckInterval.toMillis(),
                recoveryCheckInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void recover() {
        if (closed || !started) {
            return;
        }
        try {
            boolean healthy = transport.isSubscriptionHealthy();
            if (!healthy) {
                if (available) {
                    log.warn("SSE 알림 Redis 구독이 중단되어 연결을 재동기화합니다.");
                }
                available = false;
                connections.setDeliveryAvailable(false);
                transport.unsubscribe();
                transport.subscribe(signal -> localNotifier.notificationCreated(Set.of(signal.userId())));
                healthy = transport.isSubscriptionHealthy();
            }
            if (healthy) {
                if (!available) {
                    available = true;
                    connections.setDeliveryAvailable(true);
                    log.info("SSE 알림 Redis 구독이 준비되었습니다.");
                }
                failureLogged = false;
            } else {
                available = false;
                connections.setDeliveryAvailable(false);
            }
        } catch (RuntimeException exception) {
            available = false;
            connections.setDeliveryAvailable(false);
            if (!failureLogged) {
                log.warn("SSE 알림 Redis 구독을 복구하지 못했습니다: failureType={}",
                        exception.getClass().getSimpleName());
                failureLogged = true;
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        started = false;
        available = false;
        monitor.shutdownNow();
        connections.setDeliveryAvailable(false);
        transport.unsubscribe();
    }
}
