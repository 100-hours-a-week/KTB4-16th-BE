package com.ktb4.team16.mulo.memorysearch.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import com.ktb4.team16.mulo.memorysearch.service.MemorySearchInFlightRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import tools.jackson.databind.json.JsonMapper;

class MemorySearchConversationConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(MemorySearchConversationConfiguration.class,
                    InMemoryMemorySearchConversationStore.class, MemorySearchInFlightRegistry.class,
                    RedisConfiguration.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("memory-search.conversation-idle-ttl=30m");

    /** 기본 모드는 Redis 접속 없이 기존 인메모리 저장소 하나만 사용한다. */
    @Test
    void defaultsToInMemoryWithoutRedisConnection() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MemorySearchConversationStore.class);
            assertThat(context.getBean(MemorySearchConversationStore.class))
                    .isInstanceOf(InMemoryMemorySearchConversationStore.class);
            assertThat(context).hasSingleBean(MemorySearchInFlightRegistry.class);
            assertThat(context.getSourceApplicationContext().getBeanFactory()
                    .containsSingleton("redisConnection")).isFalse();
        });
    }

    /** Redis 모드에서도 최초 사용 전까지 연결하지 않으며 구현체는 하나만 선택한다. */
    @Test
    void selectsExactlyOneRedisStore() {
        runner.withPropertyValues("memory-search.conversation-store=redis",
                "memory-search.conversation-environment=test").run(context -> {
                    assertThat(context).hasSingleBean(MemorySearchConversationStore.class);
                    assertThat(context.getBean(MemorySearchConversationStore.class))
                            .isInstanceOf(RedisMemorySearchConversationStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryMemorySearchConversationStore.class);
                    assertThat(context).hasSingleBean(MemorySearchInFlightRegistry.class);
                    assertThat(context.getSourceApplicationContext().getBeanFactory()
                            .containsSingleton("redisConnection")).isFalse();
                });
    }

    /** 인기곡 집계 캐시가 꺼져 있어도 Redis 문맥 저장소를 선택할 수 있다. */
    @Test
    void redisModeDoesNotDependOnPopularCacheFlag() {
        runner.withPropertyValues("memory-search.conversation-store=redis", "mulo.popular-cache.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(RedisMemorySearchConversationStore.class));
    }

    /** 오타가 난 모드는 조용히 인메모리로 돌아가지 않고 시작 단계에서 거절한다. */
    @Test
    void rejectsUnknownStoreMode() {
        runner.withPropertyValues("memory-search.conversation-store=redsi")
                .run(context -> assertThat(context).hasFailed());
    }
}
