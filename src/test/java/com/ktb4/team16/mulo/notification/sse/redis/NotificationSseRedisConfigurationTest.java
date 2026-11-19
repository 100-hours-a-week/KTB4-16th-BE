package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb4.team16.mulo.global.redis.RedisConnectionProperties;
import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

class NotificationSseRedisConfigurationTest {

    @Test
    void localModeDoesNotCreateRedisConnectionOrStartSubscription() {
        RedisClient redisClient = mock(RedisClient.class);
        LocalNotificationCreatedNotifier localNotifier = mock(LocalNotificationCreatedNotifier.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(RedisClient.class, () -> redisClient);
            context.registerBean(RedisConnectionProperties.class, () -> new RedisConnectionProperties(
                    "127.0.0.1", 16382, Duration.ofMillis(300), Duration.ofMillis(100), Duration.ofSeconds(5)));
            context.registerBean(LocalNotificationCreatedNotifier.class, () -> localNotifier);
            context.register(NotificationSseRedisConfiguration.class);
            context.refresh();

            assertThat(context.getBeanNamesForType(NotificationCreatedNotifier.class)).hasSize(1);
            assertThat(context.getBean(NotificationCreatedNotifier.class)).isSameAs(localNotifier);
            context.getBean(NotificationSseRedisPublisher.class);
            context.getBean(NotificationSseRedisSubscriber.class);
            verifyNoInteractions(redisClient);
        }
        verify(redisClient, never()).connect();
        verify(redisClient, never()).connectPubSub();
    }
}
