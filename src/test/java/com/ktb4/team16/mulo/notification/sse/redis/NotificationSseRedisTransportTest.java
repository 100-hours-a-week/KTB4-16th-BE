package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class NotificationSseRedisTransportTest {

    private static final String CHANNEL = "mulo:test:notification:sse:created:v1";

    private final RedisClient redisClient = mock(RedisClient.class);
    private final NotificationSseSignalCodec codec = new NotificationSseSignalCodec(new ObjectMapper());

    @Test
    void constructorDoesNotOpenRedisConnection() {
        NotificationSseRedisTransport transport = transport();

        verifyNoInteractions(redisClient);
        transport.close();
        verifyNoInteractions(redisClient);
    }

    @Test
    void publishUsesOrdinaryConnectionOnlyAndClosesIt() {
        StatefulRedisConnection<String, String> publisher = publisher();
        RedisCommands<String, String> commands = publisherCommands(publisher);
        when(commands.publish(anyString(), anyString())).thenReturn(2L);
        NotificationSseRedisTransport transport = transport();

        assertThat(transport.publish(NotificationSseSignal.forRecipient(42L))).isEqualTo(2L);

        verify(redisClient).connect();
        verify(redisClient, never()).connectPubSub();
        verify(publisher).setTimeout(Duration.ofMillis(100));
        verify(commands).publish(CHANNEL, codec.encode(NotificationSseSignal.forRecipient(42L)));
        transport.close();
        verify(publisher).close();
    }

    @Test
    void subscribeUsesSeparateConnectionAndCleansListenerOnUnsubscribe() {
        StatefulRedisPubSubConnection<String, String> subscriber = subscriber();
        RedisPubSubCommands<String, String> commands = subscriberCommands(subscriber);
        NotificationSseRedisTransport transport = transport();
        List<NotificationSseSignal> received = new ArrayList<>();

        transport.subscribe(received::add);

        RedisPubSubListener<String, String> listener = capturedListener(subscriber);
        listener.message(CHANNEL, codec.encode(NotificationSseSignal.forRecipient(42L)));
        listener.message(CHANNEL, "잘못된 메시지");
        listener.message("다른-채널", codec.encode(NotificationSseSignal.forRecipient(99L)));
        assertThat(received).containsExactly(NotificationSseSignal.forRecipient(42L));
        verify(redisClient).connectPubSub();
        verify(redisClient, never()).connect();
        verify(subscriber).setTimeout(Duration.ofMillis(100));
        verify(commands).subscribe(CHANNEL);

        transport.unsubscribe();
        listener.message(CHANNEL, codec.encode(NotificationSseSignal.forRecipient(43L)));
        assertThat(received).hasSize(1);
        verify(commands).unsubscribe(CHANNEL);
        verify(subscriber).removeListener(listener);
        verify(subscriber).close();
        transport.close();
    }

    @Test
    void failedSubscribeReleasesListenerAndConnection() {
        StatefulRedisPubSubConnection<String, String> subscriber = subscriber();
        RedisPubSubCommands<String, String> commands = subscriberCommands(subscriber);
        doThrow(new IllegalStateException("구독 실패")).when(commands).subscribe(CHANNEL);
        NotificationSseRedisTransport transport = transport();

        assertThatThrownBy(() -> transport.subscribe(signal -> { }))
                .isInstanceOf(IllegalStateException.class);

        RedisPubSubListener<String, String> listener = capturedListener(subscriber);
        verify(subscriber).removeListener(listener);
        verify(subscriber).close();
        transport.close();
    }

    @Test
    void failedPublisherSetupClosesItsConnection() {
        StatefulRedisConnection<String, String> publisher = publisher();
        doThrow(new IllegalStateException("연결 준비 실패"))
                .when(publisher).setTimeout(Duration.ofMillis(100));
        NotificationSseRedisTransport transport = transport();

        assertThatThrownBy(() -> transport.publish(NotificationSseSignal.forRecipient(42L)))
                .isInstanceOf(IllegalStateException.class);

        verify(publisher).close();
        transport.close();
    }

    @Test
    void failedListenerRemovalStillClosesSubscriber() {
        StatefulRedisPubSubConnection<String, String> subscriber = subscriber();
        subscriberCommands(subscriber);
        doThrow(new IllegalStateException("리스너 정리 실패"))
                .when(subscriber).removeListener(any(RedisPubSubListener.class));
        NotificationSseRedisTransport transport = transport();
        transport.subscribe(signal -> { });

        assertThatThrownBy(transport::unsubscribe).isInstanceOf(IllegalStateException.class);

        verify(subscriber).close();
        transport.close();
    }

    @Test
    void receiverFailureDoesNotEscapeRedisListener() {
        StatefulRedisPubSubConnection<String, String> subscriber = subscriber();
        subscriberCommands(subscriber);
        NotificationSseRedisTransport transport = transport();
        transport.subscribe(signal -> {
            throw new IllegalStateException("수신 처리 실패");
        });

        RedisPubSubListener<String, String> listener = capturedListener(subscriber);
        listener.message(CHANNEL, codec.encode(NotificationSseSignal.forRecipient(42L)));

        transport.close();
        verify(subscriber).close();
    }

    @Test
    void closeReleasesPublisherEvenWhenUnsubscribeFails() {
        StatefulRedisConnection<String, String> publisher = publisher();
        publisherCommands(publisher);
        StatefulRedisPubSubConnection<String, String> subscriber = subscriber();
        RedisPubSubCommands<String, String> commands = subscriberCommands(subscriber);
        doThrow(new IllegalStateException("구독 해제 실패")).when(commands).unsubscribe(CHANNEL);
        NotificationSseRedisTransport transport = transport();
        transport.publish(NotificationSseSignal.forRecipient(42L));
        transport.subscribe(signal -> { });

        assertThatThrownBy(transport::close).isInstanceOf(IllegalStateException.class);

        verify(subscriber).close();
        verify(publisher).close();
        transport.close();
    }

    @Test
    void rejectsUseAfterClose() {
        NotificationSseRedisTransport transport = transport();
        transport.close();

        assertThatThrownBy(() -> transport.publish(NotificationSseSignal.forRecipient(42L)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transport.subscribe(signal -> { }))
                .isInstanceOf(IllegalStateException.class);
    }

    private NotificationSseRedisTransport transport() {
        return new NotificationSseRedisTransport(redisClient, codec, CHANNEL, Duration.ofMillis(100));
    }

    @SuppressWarnings("unchecked")
    private StatefulRedisConnection<String, String> publisher() {
        StatefulRedisConnection<String, String> connection = mock(StatefulRedisConnection.class);
        when(redisClient.connect()).thenReturn(connection);
        return connection;
    }

    @SuppressWarnings("unchecked")
    private RedisCommands<String, String> publisherCommands(StatefulRedisConnection<String, String> connection) {
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(connection.sync()).thenReturn(commands);
        return commands;
    }

    @SuppressWarnings("unchecked")
    private StatefulRedisPubSubConnection<String, String> subscriber() {
        StatefulRedisPubSubConnection<String, String> connection = mock(StatefulRedisPubSubConnection.class);
        when(redisClient.connectPubSub()).thenReturn(connection);
        return connection;
    }

    @SuppressWarnings("unchecked")
    private RedisPubSubCommands<String, String> subscriberCommands(
            StatefulRedisPubSubConnection<String, String> connection) {
        RedisPubSubCommands<String, String> commands = mock(RedisPubSubCommands.class);
        when(connection.sync()).thenReturn(commands);
        return commands;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RedisPubSubListener<String, String> capturedListener(
            StatefulRedisPubSubConnection<String, String> connection) {
        ArgumentCaptor<RedisPubSubListener> captor = ArgumentCaptor.forClass(RedisPubSubListener.class);
        verify(connection).addListener(captor.capture());
        return captor.getValue();
    }
}
