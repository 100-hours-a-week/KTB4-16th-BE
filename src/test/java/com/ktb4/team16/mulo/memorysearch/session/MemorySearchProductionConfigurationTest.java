package com.ktb4.team16.mulo.memorysearch.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import com.ktb4.team16.mulo.memorysearch.service.MemorySearchInFlightRegistry;
import com.ktb4.team16.mulo.memorysearch.service.RedisMemorySearchInFlightRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import tools.jackson.databind.json.JsonMapper;

class MemorySearchProductionConfigurationTest {
    /** 실제 프로필 YAML을 읽되 DB·AI·Redis 네트워크 연결은 생성하지 않는다. */
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(MemorySearchConversationConfiguration.class,
                        InMemoryMemorySearchConversationStore.class, MemorySearchInFlightRegistry.class,
                        RedisConfiguration.class)
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build());
    }

    /** 운영 프로필은 명시적인 공통 Redis 주소·포트·환경 구분이 없으면 시작하지 않는다. */
    @Test
    void productionWithoutSettingsFailsAtStartup() {
        runner("prod").run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("MEMORY_SEARCH_CONVERSATION_ENVIRONMENT"));
    }

    /** Redis 설정이 갖춰지면 운영 기본 모드로 문맥과 잠금 구현체를 선택한다. */
    @Test
    void productionSelectsRedisWithoutOpeningConnection() {
        configuredProduction().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RedisMemorySearchConversationStore.class);
            assertThat(context).hasSingleBean(RedisMemorySearchInFlightRegistry.class);
            assertThat(context).doesNotHaveBean(InMemoryMemorySearchConversationStore.class);
            assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
        });
    }

    /** 운영 서버가 로컬 저장소나 잘못된 공유 대상으로 기동하는 회귀를 차단한다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "memory-search.conversation-store=in-memory|conversation-store=redis",
            "mulo.redis.host=|REDIS_HOST", "mulo.redis.host=127.0.0.1|REDIS_HOST",
            "mulo.redis.host=localhost|REDIS_HOST", "mulo.redis.host=::1|REDIS_HOST",
            "mulo.redis.port=0|REDIS_PORT", "mulo.redis.port=65536|REDIS_PORT",
            "memory-search.conversation-environment=|MEMORY_SEARCH_CONVERSATION_ENVIRONMENT",
            "memory-search.conversation-environment=local|MEMORY_SEARCH_CONVERSATION_ENVIRONMENT"})
    void rejectsInvalidProductionSettings(String invalidSetting, String expectedReason) {
        configuredProduction().withPropertyValues(invalidSetting)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining(expectedReason));
    }

    /** 로컬 실행은 공통 Redis 없이 인메모리 문맥과 요청 잠금을 선택한다. */
    @Test
    void localUsesInMemoryWithoutRedis() {
        runner("local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InMemoryMemorySearchConversationStore.class);
            assertThat(context).hasSingleBean(MemorySearchInFlightRegistry.class);
            assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
        });
    }

    /** ECS에서 운영 프로필 자체를 빠뜨려 인메모리로 기동하는 경우도 거절한다. */
    @Test
    void ecsWithoutProductionProfileFails() {
        runner("local").withPropertyValues("AWS_EXECUTION_ENV=AWS_ECS_FARGATE")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("ECS requires the prod Spring profile"));
    }

    /** 실제 접속하지 않는 예시 DNS를 사용해 설정 검증만 수행한다. */
    private ApplicationContextRunner configuredProduction() {
        return runner("prod").withPropertyValues("mulo.redis.host=redis.example.invalid",
                "mulo.redis.port=6379", "memory-search.conversation-environment=production");
    }
}
