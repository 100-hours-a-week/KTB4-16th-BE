package com.ktb4.team16.mulo.notification.sse.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationSseRedisTransport implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseRedisTransport.class);

    private final RedisClient redisClient;
    private final NotificationSseSignalCodec codec;
    private final String channel;
    private final Duration commandTimeout;

    private StatefulRedisConnection<String, String> publisher;
    private Subscription subscription;
    private boolean closed;

    public NotificationSseRedisTransport(RedisClient redisClient, NotificationSseSignalCodec codec,
            String channel, Duration commandTimeout) {
        this.redisClient = Objects.requireNonNull(redisClient);
        this.codec = Objects.requireNonNull(codec);
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("SSE 알림 Redis 채널이 필요합니다.");
        }
        if (commandTimeout == null || commandTimeout.isZero() || commandTimeout.isNegative()) {
            throw new IllegalArgumentException("SSE 알림 Redis 명령 제한시간은 양수여야 합니다.");
        }
        this.channel = channel;
        this.commandTimeout = commandTimeout;
    }

    public synchronized long publish(NotificationSseSignal signal) {
        requireOpen();
        String message = codec.encode(signal);
        if (publisher == null) {
            StatefulRedisConnection<String, String> connection = redisClient.connect();
            try {
                connection.setTimeout(commandTimeout);
                publisher = connection;
            } catch (RuntimeException exception) {
                try {
                    connection.close();
                } catch (RuntimeException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
        }
        try {
            return publisher.sync().publish(channel, message);
        } catch (RuntimeException exception) {
            try {
                publisher.close();
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            } finally {
                publisher = null;
            }
            throw exception;
        }
    }

    public synchronized void subscribe(Consumer<NotificationSseSignal> receiver) {
        requireOpen();
        Objects.requireNonNull(receiver);
        if (subscription != null) {
            throw new IllegalStateException("SSE 알림 Redis 채널을 이미 구독하고 있습니다.");
        }
        StatefulRedisPubSubConnection<String, String> connection = redisClient.connectPubSub();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean healthy = new AtomicBoolean(true);
        RedisConnectionStateListener stateListener = new RedisConnectionStateListener() {
            @Override
            public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                healthy.set(false);
            }

            @Override
            public void onRedisExceptionCaught(RedisChannelHandler<?, ?> connection, Throwable cause) {
                healthy.set(false);
            }
        };
        RedisPubSubListener<String, String> listener = new RedisPubSubAdapter<>() {
            @Override
            public void message(String receivedChannel, String message) {
                if (!active.get() || !healthy.get() || !channel.equals(receivedChannel)) {
                    return;
                }
                try {
                    codec.decode(message).ifPresent(signal -> {
                        if (!active.get() || !healthy.get()) {
                            return;
                        }
                        receiver.accept(signal);
                    });
                } catch (RuntimeException exception) {
                    healthy.set(false);
                    log.warn("SSE 알림 Redis 신호 수신 처리에 실패했습니다: failureType={}",
                            exception.getClass().getSimpleName());
                }
            }

            @Override
            public void unsubscribed(String receivedChannel, long count) {
                if (channel.equals(receivedChannel)) {
                    healthy.set(false);
                }
            }
        };
        Subscription current = new Subscription(connection, listener, stateListener, active, healthy);
        try {
            connection.setTimeout(commandTimeout);
            connection.addListener(listener);
            connection.addListener(stateListener);
            connection.sync().subscribe(channel);
            subscription = current;
        } catch (RuntimeException exception) {
            try {
                releaseSubscription(current, false);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public synchronized boolean isSubscriptionHealthy() {
        Subscription current = subscription;
        if (current == null || !current.active().get() || !current.healthy().get()) {
            return false;
        }
        try {
            if (current.connection().isOpen() && "PONG".equals(current.connection().sync().ping())
                    && current.healthy().get()) {
                return true;
            }
        } catch (RuntimeException exception) {
            log.debug("SSE 알림 Redis 구독 상태를 확인하지 못했습니다: failureType={}",
                    exception.getClass().getSimpleName());
        }
        current.healthy().set(false);
        return false;
    }

    public synchronized void unsubscribe() {
        Subscription current = subscription;
        subscription = null;
        if (current != null) {
            releaseSubscription(current, true);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        try {
            unsubscribe();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        if (publisher != null) {
            try {
                publisher.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            } finally {
                publisher = null;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void releaseSubscription(Subscription current, boolean unsubscribe) {
        current.active().set(false);
        RuntimeException failure = null;
        if (unsubscribe && current.healthy().get() && current.connection().isOpen()) {
            try {
                current.connection().sync().unsubscribe(channel);
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        try {
            current.connection().removeListener(current.listener());
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        try {
            current.connection().removeListener(current.stateListener());
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        try {
            current.connection().close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("종료된 SSE 알림 Redis 전송은 사용할 수 없습니다.");
        }
    }

    private record Subscription(StatefulRedisPubSubConnection<String, String> connection,
            RedisPubSubListener<String, String> listener, RedisConnectionStateListener stateListener,
            AtomicBoolean active, AtomicBoolean healthy) {
    }
}
