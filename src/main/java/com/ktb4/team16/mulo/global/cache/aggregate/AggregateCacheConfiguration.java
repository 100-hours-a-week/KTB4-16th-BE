package com.ktb4.team16.mulo.global.cache.aggregate;

import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** 공통 Redis 자원을 소비하되 인기곡 활성화 설정이 true일 때만 캐시를 등록한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mulo.popular-cache", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PopularCacheProperties.class)
public class AggregateCacheConfiguration {

    /** 공통 지연 연결을 사용해 캐시가 필요할 때만 Redis에 접근한다. */
    @Bean
    AggregateCacheStore aggregateCacheStore(
            ObjectProvider<StatefulRedisConnection<String, String>> connections) {
        return new LettuceAggregateCacheStore(connections::getObject);
    }

    /** 인기곡 정책과 기존 직렬화·계측기를 사용해 캐시 엔진을 구성한다. */
    @Bean
    AggregateResultCache aggregateResultCache(AggregateCacheStore store, PopularCacheProperties properties,
            JsonMapper mapper, MeterRegistry meters) {
        return new AggregateResultCache(store, properties, mapper, meters, Clock.systemUTC());
    }
}
