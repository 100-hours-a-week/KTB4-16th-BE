package com.ktb4.team16.mulo.global.cache.aggregate;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.json.JsonMapper;

/** 인기곡 캐시 프로필과 명시적 enabled가 모두 있을 때만 Redis 자원을 등록한다. */
@Configuration(proxyBeanMethods = false)
@Profile("popular-cache")
@ConditionalOnProperty(prefix = "mulo.aggregate-cache", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AggregateCacheProperties.class)
public class AggregateCacheConfiguration {

    /** 연결·명령 제한시간과 단절 중 명령 거부를 적용하고 종료 시 클라이언트를 닫는다. */
    @Bean(destroyMethod = "shutdown")
    RedisClient aggregateRedisClient(AggregateCacheProperties properties) {
        RedisURI uri = RedisURI.Builder.redis(properties.host(), properties.port())
                .withTimeout(properties.commandTimeout()).build();
        RedisClient client = RedisClient.create(uri);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(properties.connectTimeout()).build())
                .timeoutOptions(TimeoutOptions.enabled(properties.commandTimeout()))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
        return client;
    }

    /** 첫 Redis 요청까지 연결을 지연하고 만들어진 연결은 컨텍스트 종료 시 닫는다. */
    @Bean(destroyMethod = "close")
    @Lazy
    StatefulRedisConnection<String, String> aggregateRedisConnection(RedisClient aggregateRedisClient) {
        return aggregateRedisClient.connect();
    }

    /** 지연 연결 공급자를 주입해 Redis 초기 장애가 전체 앱 기동을 막지 않게 한다. */
    @Bean
    AggregateCacheStore aggregateCacheStore(
            ObjectProvider<StatefulRedisConnection<String, String>> connections) {
        return new LettuceAggregateCacheStore(connections::getObject);
    }

    /** 기존 JsonMapper·계측기를 사용하며 DB용 Clock 빈을 추가하지 않는다. */
    @Bean
    AggregateResultCache aggregateResultCache(AggregateCacheStore store, AggregateCacheProperties properties,
            JsonMapper mapper, MeterRegistry meters) {
        return new AggregateResultCache(store, properties, mapper, meters, Clock.systemUTC());
    }
}
