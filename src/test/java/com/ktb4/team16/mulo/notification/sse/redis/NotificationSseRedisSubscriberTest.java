package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class NotificationSseRedisSubscriberTest {

    private static final String CHANNEL = "mulo:test:notification:sse:created:v1";

    private final RedisClient redisClient = mock(RedisClient.class);
    private final LocalNotificationCreatedNotifier localNotifier = mock(LocalNotificationCreatedNotifier.class);
    private final NotificationSseSignalCodec codec = new NotificationSseSignalCodec(new ObjectMapper());

    @Test
    void validSignalUsesLocalDeliveryWithoutRepublishing() {
        StatefulRedisPubSubConnection<String, String> connection = connection();
        NotificationSseRedisTransport transport = transport();
        NotificationSseRedisSubscriber subscriber = new NotificationSseRedisSubscriber(transport, localNotifier);

        subscriber.start();
        listener(connection).message(CHANNEL, codec.encode(NotificationSseSignal.forRecipient(42L)));

        verify(localNotifier).notificationCreated(Set.of(42L));
        verify(redisClient, never()).connect();
        subscriber.close();
        verify(connection).close();
    }

    @Test
    void invalidSignalIsIgnoredWithoutLocalDelivery() {
        StatefulRedisPubSubConnection<String, String> connection = connection();
        NotificationSseRedisTransport transport = transport();
        NotificationSseRedisSubscriber subscriber = new NotificationSseRedisSubscriber(transport, localNotifier);

        subscriber.start();
        listener(connection).message(CHANNEL, "{\"version\":2,\"userId\":42}");
        listener(connection).message(CHANNEL, "잘못된 JSON");

        verifyNoInteractions(localNotifier);
        subscriber.close();
    }

    @Test
    void repeatedStartSubscribesOnceAndCloseUnsubscribesOnce() {
        StatefulRedisPubSubConnection<String, String> connection = connection();
        RedisPubSubCommands<String, String> commands = connection.sync();
        NotificationSseRedisTransport transport = transport();
        NotificationSseRedisSubscriber subscriber = new NotificationSseRedisSubscriber(transport, localNotifier);

        subscriber.start();
        subscriber.start();
        subscriber.close();
        subscriber.close();

        verify(commands).subscribe(CHANNEL);
        verify(commands).unsubscribe(CHANNEL);
        verify(connection).close();
        assertThatThrownBy(subscriber::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedStartReleasesConnection() {
        StatefulRedisPubSubConnection<String, String> connection = connection();
        RedisPubSubCommands<String, String> commands = connection.sync();
        doThrow(new IllegalStateException("Redis 구독 실패")).when(commands).subscribe(CHANNEL);
        NotificationSseRedisTransport transport = transport();
        NotificationSseRedisSubscriber subscriber = new NotificationSseRedisSubscriber(transport, localNotifier);

        assertThatThrownBy(subscriber::start).isInstanceOf(IllegalStateException.class);

        verify(connection).close();
        subscriber.close();
    }

    @SuppressWarnings("unchecked")
    private StatefulRedisPubSubConnection<String, String> connection() {
        StatefulRedisPubSubConnection<String, String> connection = mock(StatefulRedisPubSubConnection.class);
        RedisPubSubCommands<String, String> commands = mock(RedisPubSubCommands.class);
        when(redisClient.connectPubSub()).thenReturn(connection);
        when(connection.sync()).thenReturn(commands);
        return connection;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RedisPubSubListener<String, String> listener(StatefulRedisPubSubConnection<String, String> connection) {
        ArgumentCaptor<RedisPubSubListener> captor = ArgumentCaptor.forClass(RedisPubSubListener.class);
        verify(connection).addListener(captor.capture());
        return captor.getValue();
    }

    private NotificationSseRedisTransport transport() {
        return new NotificationSseRedisTransport(redisClient, codec, CHANNEL, Duration.ofMillis(100));
    }
}
