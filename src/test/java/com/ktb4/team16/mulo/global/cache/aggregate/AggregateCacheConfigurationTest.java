package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class AggregateCacheConfigurationTest {

    /** 기본 실행에서 캐시와 Redis 클라이언트 자체가 등록되지 않는지 검증한다. */
    @Test
    void defaultContextDoesNotCreateCacheOrRedisClient() {
        runner().run(context -> assertThat(context)
                .doesNotHaveBean(AggregateResultCache.class).doesNotHaveBean(RedisClient.class));
    }

    /** enabled만 설정해 일반 프로필의 Redis 연결이 생기지 않는지 검증한다. */
    @Test
    void enabledFlagWithoutCacheProfileDoesNotCreateCache() {
        runner().withPropertyValues("mulo.aggregate-cache.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(AggregateResultCache.class)
                        .doesNotHaveBean(RedisClient.class));
    }

    /** 실험 프로필만으로는 명시적 enabled 요구를 건너뛰지 않는지 검증한다. */
    @Test
    void cacheProfileWithoutEnabledFlagDoesNotCreateCache() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .run(context -> assertThat(context).doesNotHaveBean(AggregateResultCache.class)
                        .doesNotHaveBean(RedisClient.class));
    }

    /** 두 조건이 맞으면 캐시를 등록하되 첫 요청 전에는 외부 연결을 열지 않는지 검증한다. */
    @Test
    void bothConditionsCreateCacheWithoutOpeningRedisConnectionAtStartup() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .withPropertyValues("mulo.aggregate-cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AggregateResultCache.class).hasSingleBean(RedisClient.class);
                    assertThat(context.getBeanFactory().containsSingleton("aggregateRedisConnection")).isFalse();
                });
    }

    /** 실제 설정값이 무인자 생성자의 기본값 대신 캐시 엔진에 바인딩되는지 검증한다. */
    @Test
    void enabledAndCustomTimeoutBindToImmutableProperties() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .withPropertyValues("mulo.aggregate-cache.enabled=true", "mulo.aggregate-cache.port=16381",
                        "mulo.aggregate-cache.namespace=mulo:binding-test", "mulo.aggregate-cache.ttl=17s")
                .run(context -> {
                    AggregateCacheProperties properties = context.getBean(AggregateCacheProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.port()).isEqualTo(16381);
                    assertThat(properties.namespace()).isEqualTo("mulo:binding-test");
                    assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(17));
                });
    }

    /** 실제 애플리케이션 외부 자원 없이 설정 경계가 소비하는 빈만 제공한다. */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AggregateCacheConfiguration.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }
}
