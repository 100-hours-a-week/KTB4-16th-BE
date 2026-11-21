package com.ktb4.team16.mulo.notification.sse.redis;

import com.ktb4.team16.mulo.global.redis.RedisConnectionProperties;
import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class NotificationSseRedisConfiguration {

    private static final String CHANNEL = "mulo:notification:sse:created:v1";

    public NotificationSseRedisConfiguration(@Value("${mulo.notification.sse.mode:local}") String mode) {
        if (!"local".equals(mode) && !"redis".equals(mode)) {
            throw new IllegalArgumentException("SSE 알림 전달 모드는 local 또는 redis여야 합니다.");
        }
    }

    @Bean
    NotificationSseSignalCodec notificationSseSignalCodec(ObjectMapper objectMapper) {
        return new NotificationSseSignalCodec(objectMapper);
    }

    @Bean(destroyMethod = "close")
    @Lazy
    NotificationSseRedisTransport notificationSseRedisTransport(RedisClient redisClient,
            NotificationSseSignalCodec codec, RedisConnectionProperties properties) {
        return new NotificationSseRedisTransport(redisClient, codec, CHANNEL, properties.commandTimeout());
    }

    @Bean
    @Lazy
    NotificationSseRedisPublisher notificationSseRedisPublisher(NotificationSseRedisTransport transport) {
        return new NotificationSseRedisPublisher(transport);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "mulo.notification.sse", name = "mode", havingValue = "redis")
    NotificationSseRedisSubscriber notificationSseRedisSubscriber(NotificationSseRedisTransport transport,
            LocalNotificationCreatedNotifier localNotifier, NotificationSseConnectionManager connections,
            @Value("${mulo.notification.sse.recovery-check-interval:PT1S}") Duration recoveryCheckInterval) {
        return new NotificationSseRedisSubscriber(transport, localNotifier, connections, recoveryCheckInterval);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "mulo.notification.sse", name = "mode", havingValue = "redis")
    RedisNotificationCreatedNotifier redisNotificationCreatedNotifier(NotificationSseRedisPublisher publisher,
            @Qualifier("notificationSseDeliveryExecutor") ThreadPoolTaskExecutor executor,
            NotificationSseConnectionManager connections) {
        return new RedisNotificationCreatedNotifier(publisher, executor, connections);
    }
}
