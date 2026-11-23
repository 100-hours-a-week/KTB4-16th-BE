package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.redis.RedisConnectionProperties;
import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import com.ktb4.team16.mulo.notification.sse.NotificationSseDeliveryConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class NotificationSseRedisConfigurationTest {

    private static final String CHANNEL = "mulo:notification:sse:created:v1";

    private final RedisClient redisClient = mock(RedisClient.class);
    private final NotificationSseConnectionManager connections = mock(NotificationSseConnectionManager.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(
                    ApplicationConversionService.getSharedInstance()))
            .withPropertyValues("mulo.notification.sse.recovery-check-interval=PT1H")
            .withUserConfiguration(NotificationSseRedisConfiguration.class,
                    NotificationSseDeliveryConfiguration.class, LocalNotificationCreatedNotifier.class)
            .withBean(RedisClient.class, () -> redisClient)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(NotificationSseConnectionManager.class, () -> connections)
            .withBean(RedisConnectionProperties.class, () -> new RedisConnectionProperties(
                    "127.0.0.1", 16382, Duration.ofMillis(300), Duration.ofMillis(100), Duration.ofSeconds(5)));

    @Test
    void defaultModeKeepsLocalDeliveryWithoutRedisConnections() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(NotificationCreatedNotifier.class);
            assertThat(context.getBean(NotificationCreatedNotifier.class))
                    .isSameAs(context.getBean(LocalNotificationCreatedNotifier.class));
            assertThat(context).doesNotHaveBean(NotificationSseRedisSubscriber.class);
            context.getBean(NotificationSseRedisPublisher.class);
            context.getBean(NotificationCreatedNotifier.class).notificationCreated(Set.of(42L));
            verify(connections, timeout(1000)).sendNotificationCreated(42L);
        });
        verify(redisClient, never()).connect();
        verify(redisClient, never()).connectPubSub();
    }

    @Test
    void explicitLocalModeDoesNotStartRedisSubscription() {
        runner.withPropertyValues("mulo.notification.sse.mode=local").run(context -> {
            assertThat(context).hasSingleBean(NotificationCreatedNotifier.class);
            assertThat(context).doesNotHaveBean(NotificationSseRedisSubscriber.class);
        });
        verify(redisClient, never()).connect();
        verify(redisClient, never()).connectPubSub();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void redisModeSubscribesPublishesAndDeliversLocallyOnlyThroughSubscription() {
        StatefulRedisPubSubConnection<String, String> subscriber = mock(StatefulRedisPubSubConnection.class);
        RedisPubSubCommands<String, String> subscriptionCommands = mock(RedisPubSubCommands.class);
        StatefulRedisConnection<String, String> publisher = mock(StatefulRedisConnection.class);
        RedisCommands<String, String> publishCommands = mock(RedisCommands.class);
        when(redisClient.connectPubSub()).thenReturn(subscriber);
        when(subscriber.sync()).thenReturn(subscriptionCommands);
        when(subscriber.isOpen()).thenReturn(true);
        when(subscriptionCommands.ping()).thenReturn("PONG");
        when(redisClient.connect()).thenReturn(publisher);
        when(publisher.sync()).thenReturn(publishCommands);

        runner.withPropertyValues("mulo.notification.sse.mode=redis").run(context -> {
            assertThat(context.getBean(NotificationCreatedNotifier.class))
                    .isInstanceOf(RedisNotificationCreatedNotifier.class);
            verify(subscriptionCommands).subscribe(CHANNEL);
            verify(redisClient, never()).connect();

            context.getBean(NotificationCreatedNotifier.class).notificationCreated(Set.of(42L));
            String signal = context.getBean(NotificationSseSignalCodec.class)
                    .encode(NotificationSseSignal.forRecipient(42L));
            verify(publishCommands, timeout(1000)).publish(CHANNEL, signal);
            verify(connections, never()).sendNotificationCreated(42L);

            ArgumentCaptor<RedisPubSubListener> listener = ArgumentCaptor.forClass(RedisPubSubListener.class);
            verify(subscriber).addListener(listener.capture());
            listener.getValue().message(CHANNEL, signal);

            verify(connections, timeout(1000)).sendNotificationCreated(42L);
            verify(publishCommands).publish(CHANNEL, signal);
        });

        verify(subscriptionCommands).unsubscribe(CHANNEL);
        verify(subscriber).close();
        verify(publisher).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "redsi", "true"})
    void invalidModeFailsStartup(String mode) {
        runner.withPropertyValues("mulo.notification.sse.mode=" + mode).run(context ->
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class));
        verify(redisClient, never()).connect();
        verify(redisClient, never()).connectPubSub();
    }

    @Test
    void failedRedisSubscriptionKeepsApplicationRunningWithDeliveryUnavailable() {
        when(redisClient.connectPubSub()).thenThrow(new IllegalStateException("test subscription failure"));
        runner.withPropertyValues("mulo.notification.sse.mode=redis").run(context -> {
            assertThat(context).hasNotFailed();
            verify(connections, never()).setDeliveryAvailable(true);
        });
        verify(redisClient, never()).connect();
    }
}
