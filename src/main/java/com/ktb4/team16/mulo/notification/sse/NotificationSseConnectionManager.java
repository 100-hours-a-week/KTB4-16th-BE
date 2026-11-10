package com.ktb4.team16.mulo.notification.sse;

import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.global.exception.AsyncRequestFailureClassifier;
import com.ktb4.team16.mulo.global.security.session.SessionRevocationStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class NotificationSseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseConnectionManager.class);

    private final ConcurrentMap<Long, ConcurrentMap<UUID, Connection>> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer;
    private final Clock clock;
    private final Duration heartbeatInterval;
    private final Duration maximumConnectionDuration;
    private final SessionRevocationStore sessionRevocationStore;

    public NotificationSseConnectionManager(Clock clock,
            @Value("${mulo.notification.sse.heartbeat-interval:PT25S}") Duration heartbeatInterval,
            @Value("${mulo.notification.sse.maximum-connection-duration:PT1H}") Duration maximumConnectionDuration,
            SessionRevocationStore sessionRevocationStore) {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()
                || maximumConnectionDuration.isNegative() || maximumConnectionDuration.isZero()
                || heartbeatInterval.toMillis() == 0 || maximumConnectionDuration.toMillis() == 0) {
            throw new IllegalArgumentException("SSE Heartbeat와 최대 연결 시간은 양수여야 합니다.");
        }
        this.clock = clock;
        this.heartbeatInterval = heartbeatInterval;
        this.maximumConnectionDuration = maximumConnectionDuration;
        this.sessionRevocationStore = sessionRevocationStore;
        AtomicInteger sequence = new AtomicInteger();
        this.timer = Executors.newScheduledThreadPool(2, task -> {
            Thread thread = new Thread(task, "notification-sse-timer-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::heartbeat, heartbeatInterval.toMillis(),
                heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 인증된 세션의 만료·폐기 상태를 확인하고 수명이 제한된 SSE 연결을 등록한다. */
    public SseEmitter connect(Long userId, Instant tokenExpiresAt, String sessionId) {
        Instant now = clock.instant();
        if (!now.isBefore(tokenExpiresAt)) {
            throw new UnauthenticatedUserException();
        }
        if (isSessionRevoked(userId, sessionId)) {
            throw new UnauthenticatedUserException();
        }
        Instant deadline = now.plus(maximumConnectionDuration);
        if (tokenExpiresAt.isBefore(deadline)) {
            deadline = tokenExpiresAt;
        }
        long timeoutMillis = Math.max(1, Duration.between(now, deadline).toMillis() + 1);
        SseEmitter emitter = createEmitter(timeoutMillis);
        Connection connection = new Connection(UUID.randomUUID(), userId, sessionId,
                tokenExpiresAt, deadline, emitter);
        emitter.onCompletion(() -> cleanup(connection));
        emitter.onTimeout(() -> close(connection));
        emitter.onError(error -> cleanup(connection));
        connection.expirationTask = timer.schedule(() -> expire(connection), timeoutMillis, TimeUnit.MILLISECONDS);
        connections.compute(userId, (id, existing) -> {
            ConcurrentMap<UUID, Connection> userConnections = existing == null
                    ? new ConcurrentHashMap<>() : existing;
            userConnections.put(connection.id, connection);
            return userConnections;
        });
        if (connection.closed.get()) {
            remove(connection);
            throw new IllegalStateException("SSE 연결을 시작하지 못했습니다.");
        }
        if (!send(connection, SseEmitter.event().name("connected").data(Map.of()))) {
            throw new IllegalStateException("SSE 연결을 시작하지 못했습니다.");
        }
        return emitter;
    }

    public void sendNotificationCreated(Long userId) {
        ConcurrentMap<UUID, Connection> userConnections = connections.get(userId);
        if (userConnections == null) {
            return;
        }
        for (Connection connection : userConnections.values()) {
            send(connection, SseEmitter.event().name("notification-created").data(Map.of()));
        }
    }

    public void closeForResynchronization(Long userId) {
        ConcurrentMap<UUID, Connection> userConnections = connections.get(userId);
        if (userConnections == null) {
            return;
        }
        for (Connection connection : userConnections.values()) {
            close(connection);
        }
    }

    protected SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    private void heartbeat() {
        try {
            for (ConcurrentMap<UUID, Connection> userConnections : connections.values()) {
                for (Connection connection : userConnections.values()) {
                    send(connection, SseEmitter.event().comment("heartbeat"));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("SSE Heartbeat 실행 중 오류가 발생했습니다: failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void expire(Connection connection) {
        if (!connection.expiring.compareAndSet(false, true)) {
            return;
        }
        if (!clock.instant().isBefore(connection.tokenExpiresAt)) {
            sendWithoutDeadline(connection, SseEmitter.event().name("auth-expired").data(Map.of()));
        }
        close(connection);
    }

    private boolean send(Connection connection, SseEmitter.SseEventBuilder event) {
        if (connection.closed.get()) {
            return false;
        }
        if (isRevokedOrUnavailable(connection)) {
            close(connection);
            return false;
        }
        if (!clock.instant().isBefore(connection.deadline)) {
            expire(connection);
            return false;
        }
        return sendFrame(connection, event, true);
    }

    private boolean sendWithoutDeadline(Connection connection, SseEmitter.SseEventBuilder event) {
        return sendFrame(connection, event, false);
    }

    /** 세션 폐기 여부를 Redis에서 읽고, 연결 중 저장소 오류는 닫을 대상으로 처리한다. */
    private boolean isRevokedOrUnavailable(Connection connection) {
        try {
            return isSessionRevoked(connection.userId, connection.sessionId);
        } catch (RuntimeException exception) {
            log.warn("SSE 세션 폐기 상태를 확인할 수 없어 연결을 닫습니다: failureType={}",
                    exception.getClass().getSimpleName());
            return true;
        }
    }

    /** 새 연결은 세션 ID 또는 레거시 사용자 표식으로 폐기 여부를 확인한다. */
    private boolean isSessionRevoked(Long userId, String sessionId) {
        return sessionId == null
                ? sessionRevocationStore.areLegacyUserTokensRevoked(userId)
                : sessionRevocationStore.isSessionRevoked(sessionId);
    }

    private boolean sendFrame(Connection connection, SseEmitter.SseEventBuilder event, boolean checkDeadline) {
        try {
            boolean expired;
            synchronized (connection) {
                if (connection.closed.get()) {
                    return false;
                }
                expired = checkDeadline && !clock.instant().isBefore(connection.deadline);
                if (!expired) {
                    connection.emitter.send(event);
                }
            }
            if (expired) {
                expire(connection);
                return false;
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            cleanup(connection);
            if (AsyncRequestFailureClassifier.isExpectedConnectionTermination(exception)) {
                log.debug("SSE 응답 연결을 사용할 수 없어 전송을 중단했습니다: failureType={}",
                        exception.getClass().getSimpleName());
            } else if (!(exception instanceof IOException)
                    && !AsyncRequestFailureClassifier.containsAsyncRequestNotUsable(exception)) {
                log.error("SSE 이벤트 전송 중 예기치 않은 오류가 발생했습니다.", exception);
                Sentry.captureException(exception);
            }
            return false;
        }
    }

    private void close(Connection connection) {
        if (!markClosedAndRemove(connection)) {
            return;
        }
        try {
            connection.emitter.complete();
        } catch (RuntimeException exception) {
            log.error("SSE 연결 종료를 완료하지 못했습니다.", exception);
            Sentry.captureException(exception);
        }
    }

    private void cleanup(Connection connection) {
        markClosedAndRemove(connection);
    }

    private boolean markClosedAndRemove(Connection connection) {
        if (!connection.closed.compareAndSet(false, true)) {
            return false;
        }
        remove(connection);
        return true;
    }

    private void remove(Connection connection) {
        connections.computeIfPresent(connection.userId, (id, userConnections) -> {
            userConnections.remove(connection.id, connection);
            return userConnections.isEmpty() ? null : userConnections;
        });
        ScheduledFuture<?> task = connection.expirationTask;
        if (task != null) {
            task.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (ConcurrentMap<UUID, Connection> userConnections : connections.values()) {
            for (Connection connection : userConnections.values()) {
                close(connection);
            }
        }
        timer.shutdownNow();
    }

    int connectionCount(Long userId) {
        ConcurrentMap<UUID, Connection> userConnections = connections.get(userId);
        return userConnections == null ? 0 : userConnections.size();
    }

    private static final class Connection {
        private final UUID id;
        private final Long userId;
        private final String sessionId;
        private final Instant tokenExpiresAt;
        private final Instant deadline;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean expiring = new AtomicBoolean();
        private volatile ScheduledFuture<?> expirationTask;

        private Connection(UUID id, Long userId, String sessionId, Instant tokenExpiresAt,
                Instant deadline, SseEmitter emitter) {
            this.id = id;
            this.userId = userId;
            this.sessionId = sessionId;
            this.tokenExpiresAt = tokenExpiresAt;
            this.deadline = deadline;
            this.emitter = emitter;
        }
    }
}
