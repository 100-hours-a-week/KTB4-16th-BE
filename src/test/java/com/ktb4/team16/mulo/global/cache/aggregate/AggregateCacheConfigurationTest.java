package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import com.ktb4.team16.mulo.global.redis.RedisConnectionProperties;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import io.lettuce.core.RedisClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import tools.jackson.databind.json.JsonMapper;

class AggregateCacheConfigurationTest {

    /** 기본 실행에서 인기곡 캐시와 실제 Redis 자원이 생성되지 않는지 검증한다. */
    @Test
    void defaultContextDoesNotCreateCacheOrOpenRedisResources() {
        runner().run(context -> {
            assertThat(context).doesNotHaveBean(AggregateResultCache.class);
            assertThat(context).doesNotHaveBean(AggregateCacheAspect.class);
            assertThat(context).doesNotHaveBean(PopularCacheBoundaryReader.class);
            assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
            assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
        });
    }

    /** 별도 프로필 없이 enabled만으로 조회·무효화·경계 구성이 함께 등록되는지 검증한다. */
    @Test
    void enabledFlagWithoutCacheProfileCreatesAllCacheComponents() {
        runner().withPropertyValues("mulo.popular-cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AggregateResultCache.class);
                    assertThat(context).hasSingleBean(AggregateCacheAspect.class);
                    assertThat(context).hasSingleBean(PopularCacheBoundaryReader.class);
                    assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 명시적 false에서는 모든 캐시 구성을 생략하고 Redis 연결도 열지 않는지 검증한다. */
    @Test
    void disabledFlagDoesNotCreateCacheComponentsOrOpenRedis() {
        runner().withPropertyValues("mulo.popular-cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AggregateResultCache.class);
                    assertThat(context).doesNotHaveBean(AggregateCacheAspect.class);
                    assertThat(context).doesNotHaveBean(PopularCacheBoundaryReader.class);
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 기존 배포 프로필을 변경하지 않고 공통 YAML의 환경변수로 캐시를 켜는지 검증한다. */
    @Test
    void commonYamlEnvironmentEnablesCacheWithoutReplacingDeploymentProfile() {
        runner().withInitializer(context -> {
            context.getEnvironment().setActiveProfiles("deployment-test");
            context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
                            "MULO_POPULAR_CACHE_ENABLED", "true",
                            "MULO_POPULAR_CACHE_NAMESPACE", "mulo:environment-test",
                            "MULO_POPULAR_CACHE_TTL", "60s",
                            "MULO_REDIS_COMMANDTIMEOUT", "1s")));
        })
                .run(context -> {
                    assertThat(context).hasSingleBean(AggregateResultCache.class);
                    assertThat(context).hasSingleBean(AggregateCacheAspect.class);
                    assertThat(context).hasSingleBean(PopularCacheBoundaryReader.class);
                    assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("deployment-test");
                    PopularCacheProperties properties = context.getBean(PopularCacheProperties.class);
                    assertThat(properties.namespace()).isEqualTo("mulo:environment-test");
                    assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(context.getBean(RedisConnectionProperties.class).commandTimeout())
                            .isEqualTo(Duration.ofSeconds(1));
                    assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 실제 설정값이 무인자 생성자의 기본값 대신 캐시 엔진에 바인딩되는지 검증한다. */
    @Test
    void enabledAndCustomTimeoutBindToImmutableProperties() {
        runner().withPropertyValues("mulo.popular-cache.enabled=true", "mulo.redis.port=16381", "mulo.redis.connect-timeout=750ms",
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
        runner().withPropertyValues("mulo.popular-cache.enabled=true", "mulo.aggregate-cache.enabled=false")
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
        return new ApplicationContextRunner().withInitializer(context -> {
            if (context.getEnvironment().getActiveProfiles().length == 0) {
                context.getEnvironment().setActiveProfiles("test");
            }
        })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RedisConfiguration.class, AggregateCacheConfiguration.class,
                        AggregateCacheAspect.class, PopularCacheBoundaryReader.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(RecordRepository.class, () -> mock(RecordRepository.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }
}
