package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import com.ktb4.team16.mulo.global.redis.RedisConnectionProperties;
import io.lettuce.core.RedisClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class AggregateCacheConfigurationTest {

    /** 기본 실행에서 인기곡 캐시와 실제 Redis 자원이 생성되지 않는지 검증한다. */
    @Test
    void defaultContextDoesNotCreateCacheOrOpenRedisResources() {
        runner().run(context -> {
            assertThat(context).doesNotHaveBean(AggregateResultCache.class);
            assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
            assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
        });
    }

    /** enabled만 설정해 일반 프로필의 Redis 연결이 생기지 않는지 검증한다. */
    @Test
    void enabledFlagWithoutCacheProfileDoesNotCreateCache() {
        runner().withPropertyValues("mulo.popular-cache.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AggregateResultCache.class);
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 실험 프로필만으로는 명시적 enabled 요구를 건너뛰지 않는지 검증한다. */
    @Test
    void cacheProfileWithoutEnabledFlagDoesNotCreateCache() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AggregateResultCache.class);
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 두 조건이 맞으면 캐시를 등록하되 첫 요청 전에는 외부 연결을 열지 않는지 검증한다. */
    @Test
    void bothConditionsCreateCacheWithoutOpeningRedisConnectionAtStartup() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .withPropertyValues("mulo.popular-cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AggregateResultCache.class);
                    assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 실제 설정값이 무인자 생성자의 기본값 대신 캐시 엔진에 바인딩되는지 검증한다. */
    @Test
    void enabledAndCustomTimeoutBindToImmutableProperties() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .withPropertyValues("mulo.popular-cache.enabled=true", "mulo.redis.port=16381", "mulo.redis.connect-timeout=750ms",
                        "mulo.popular-cache.namespace=mulo:binding-test", "mulo.popular-cache.ttl=17s")
                .run(context -> {
                    PopularCacheProperties properties = context.getBean(PopularCacheProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    RedisConnectionProperties connection = context.getBean(RedisConnectionProperties.class);
                    assertThat(connection.port()).isEqualTo(16381);
                    assertThat(context.getBean(RedisClient.class).getOptions().getSocketOptions().getConnectTimeout())
                            .isEqualTo(Duration.ofMillis(750));
                    assertThat(properties.namespace()).isEqualTo("mulo:binding-test");
                    assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(17));
                });
    }

    /** 새 기능 설정으로 캐시가 활성화되어 이전 집계 설정에 의존하지 않는지 검증한다. */
    @Test
    void popularFeatureUsesItsOwnEnabledProperty() {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("popular-cache"))
                .withPropertyValues("mulo.popular-cache.enabled=true", "mulo.aggregate-cache.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(AggregateResultCache.class));
    }

    /** 인기곡을 꺼도 다른 기능이 공통 Redis 자원을 요청할 수 있고 연결은 지연되는지 검증한다. */
    @Test
    void sharedRedisRemainsAvailableWhenPopularCacheIsDisabled() {
        runner().withPropertyValues("mulo.popular-cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AggregateResultCache.class);
                    assertThat(context.containsBeanDefinition("redisClient")).isTrue();
                    assertThat(context.containsBeanDefinition("redisConnection")).isTrue();
                    assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                    assertThat(context.getBean(RedisClient.class)).isNotNull();
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 실제 애플리케이션 외부 자원 없이 설정 경계가 소비하는 빈만 제공한다. */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(RedisConfiguration.class, AggregateCacheConfiguration.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }
}
