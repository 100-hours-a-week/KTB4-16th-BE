package com.ktb4.team16.mulo.global.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** 인기곡 활성화 여부와 무관하게 여러 기능이 요청할 수 있는 지연 Redis 자원이다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisConnectionProperties.class)
public class RedisConfiguration {

    /** 실제 소비자가 요청할 때 공통 클라이언트를 만들고 연결·명령 제한시간을 적용한다. */
    @Bean(destroyMethod = "shutdown")
    @Lazy
    RedisClient redisClient(RedisConnectionProperties properties) {
        RedisURI uri = RedisURI.Builder.redis(properties.host(), properties.port())
                // URI 시간은 Lettuce의 HELLO 등 연결 초기화에도 사용되므로 명령 기한과 분리한다.
                .withTimeout(properties.initializationTimeout()).build();
        RedisClient client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(properties.connectTimeout()).build())
                .timeoutOptions(TimeoutOptions.enabled(properties.commandTimeout()))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
        return client;
    }

    /** 처음 요청될 때만 공통 연결을 열고 애플리케이션 종료 시 생성된 연결을 닫는다. */
    @Bean(destroyMethod = "close")
    @Lazy
    StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient,
            RedisConnectionProperties properties) {
        StatefulRedisConnection<String, String> connection = redisClient.connect();
        // 연결 준비가 끝나면 동기 API도 기존의 짧은 명령 기한으로 동작한다.
        connection.setTimeout(properties.commandTimeout());
        return connection;
    }
}
