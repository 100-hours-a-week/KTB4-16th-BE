package com.ktb4.team16.mulo.notification.sse.redis;

import com.ktb4.team16.mulo.global.redis.RedisConnectionProperties;
import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import io.lettuce.core.RedisClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class NotificationSseRedisConfiguration {

    private static final String CHANNEL = "mulo:notification:sse:created:v1";

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

    @Bean(destroyMethod = "close")
    @Lazy
    NotificationSseRedisSubscriber notificationSseRedisSubscriber(NotificationSseRedisTransport transport,
            LocalNotificationCreatedNotifier localNotifier) {
        return new NotificationSseRedisSubscriber(transport, localNotifier);
    }
}
