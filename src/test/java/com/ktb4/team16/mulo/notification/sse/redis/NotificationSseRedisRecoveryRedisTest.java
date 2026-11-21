package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import io.lettuce.core.KillArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "MULO_NOTIFICATION_SSE_REDIS_TEST", matches = "true")
class NotificationSseRedisRecoveryRedisTest {

    @Test
    void killedSubscriptionClosesStreamsAndResubscribesBeforeDeliveringAgain() throws Exception {
        RedisURI uri = RedisURI.Builder.redis("127.0.0.1", 16384).withTimeout(Duration.ofSeconds(2)).build();
        RedisClient client = RedisClient.create(uri);
        RedisClient connectionFactory = mock(RedisClient.class);
        LocalNotificationCreatedNotifier localNotifier = mock(LocalNotificationCreatedNotifier.class);
        NotificationSseConnectionManager connections = mock(NotificationSseConnectionManager.class);
        AtomicLong subscriptionId = new AtomicLong();
        AtomicBoolean available = new AtomicBoolean();
        AtomicInteger unavailableCount = new AtomicInteger();
        when(connectionFactory.connectPubSub()).thenAnswer(invocation -> {
            var connection = client.connectPubSub();
            subscriptionId.set(connection.sync().clientId());
            return connection;
        });
        when(connectionFactory.connect()).thenAnswer(invocation -> client.connect());
        doAnswer(invocation -> {
            boolean value = invocation.getArgument(0);
            available.set(value);
            if (!value) {
                unavailableCount.incrementAndGet();
            }
            return null;
        }).when(connections).setDeliveryAvailable(anyBoolean());
        String channel = "mulo:test:notification:sse:" + UUID.randomUUID();
        try (var admin = client.connect();
                var transport = new NotificationSseRedisTransport(connectionFactory,
                        new NotificationSseSignalCodec(new ObjectMapper()), channel, Duration.ofMillis(500));
                var subscriber = new NotificationSseRedisSubscriber(
                        transport, localNotifier, connections, Duration.ofMillis(50))) {
            subscriber.start();
            assertThat(available.get()).isTrue();
            transport.publish(NotificationSseSignal.forRecipient(42L));
            verify(localNotifier, timeout(2000)).notificationCreated(Set.of(42L));
            long originalId = subscriptionId.get();
            int originalUnavailableCount = unavailableCount.get();

            assertThat(admin.sync().clientKill(KillArgs.Builder.id(originalId))).isEqualTo(1L);
            await(() -> subscriptionId.get() != originalId
                    && unavailableCount.get() > originalUnavailableCount && available.get());

            transport.publish(NotificationSseSignal.forRecipient(43L));
            verify(localNotifier, timeout(2000)).notificationCreated(Set.of(43L));
            verifyNoMoreInteractions(localNotifier);
        } finally {
            client.shutdown();
        }
        assertThat(available.get()).isFalse();
    }

    @Test
    void unresponsiveRedisClosesStreamsAndRecoversAfterCommandsResume() throws Exception {
        RedisURI uri = RedisURI.Builder.redis("127.0.0.1", 16384).withTimeout(Duration.ofSeconds(2)).build();
        RedisClient client = RedisClient.create(uri);
        LocalNotificationCreatedNotifier localNotifier = mock(LocalNotificationCreatedNotifier.class);
        NotificationSseConnectionManager connections = mock(NotificationSseConnectionManager.class);
        AtomicBoolean available = new AtomicBoolean();
        AtomicInteger unavailableCount = new AtomicInteger();
        doAnswer(invocation -> {
            boolean value = invocation.getArgument(0);
            available.set(value);
            if (!value) {
                unavailableCount.incrementAndGet();
            }
            return null;
        }).when(connections).setDeliveryAvailable(anyBoolean());
        try (var admin = client.connect();
                var transport = new NotificationSseRedisTransport(client,
                        new NotificationSseSignalCodec(new ObjectMapper()),
                        "mulo:test:notification:sse:" + UUID.randomUUID(), Duration.ofMillis(100));
                var subscriber = new NotificationSseRedisSubscriber(
                        transport, localNotifier, connections, Duration.ofMillis(50))) {
            subscriber.start();
            assertThat(available.get()).isTrue();
            int originalUnavailableCount = unavailableCount.get();

            admin.sync().clientPause(1200);
            await(() -> unavailableCount.get() > originalUnavailableCount && available.get());

            transport.publish(NotificationSseSignal.forRecipient(42L));
            verify(localNotifier, timeout(2000)).notificationCreated(Set.of(42L));
        } finally {
            client.shutdown();
        }
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
