package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

/** opt-in 시 compose.redis-local.yml의 전용 Redis만 사용하는 실제 저장소 계약 검증이다. */
@EnabledIfEnvironmentVariable(named = "MULO_POPULAR_REDIS_TEST", matches = "true")
class LettuceAggregateCacheStoreLocalRedisTest {
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private LettuceAggregateCacheStore store;
    private String namespace;

    /** 고정 loopback 포트에 연결하고 테스트마다 새로운 키 영역을 사용한다. */
    @BeforeEach
    void connectToDedicatedLocalRedis() {
        client = RedisClient.create("redis://127.0.0.1:16382");
        connection = client.connect();
        store = new LettuceAggregateCacheStore(connection);
        namespace = "mulo:popular-cache-test:" + UUID.randomUUID();
    }

    /** 자신이 생성한 키만 정리하고 연결·클라이언트를 닫는다. */
    @AfterEach
    void closeOwnedResources() {
        if (connection != null) {
            for (String key : connection.sync().keys(namespace + "*")) {
                connection.sync().del(key);
            }
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    /** 인기곡이 꺼져 있어도 공통 연결로 실제 Redis를 사용할 수 있는지 검증한다. */
    @Test
    void sharedConnectionWorksWithoutPopularCache() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfiguration.class, AggregateCacheConfiguration.class)
                .withPropertyValues("mulo.redis.host=127.0.0.1", "mulo.redis.port=16382",
                        "mulo.popular-cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AggregateResultCache.class);
                    @SuppressWarnings("unchecked")
                    StatefulRedisConnection<String, String> shared =
                            context.getBean("redisConnection", StatefulRedisConnection.class);
                    assertThat(shared.sync().ping()).isEqualTo("PONG");
                    assertThat(context.getBean("redisConnection")).isSameAs(shared);
                });
    }

    /** 실제 NX와 Lua 토큰 비교가 다른 소유자의 잠금을 지우지 않는지 검증한다. */
    @Test
    void lockIsConditionalAndReleasedOnlyByItsOwner() {
        String key = namespace + ":lock";
        assertThat(store.putIfAbsent(key, "first", Duration.ofSeconds(10))).isTrue();
        assertThat(store.putIfAbsent(key, "second", Duration.ofSeconds(10))).isFalse();
        store.releaseLock(key, "second");
        assertThat(store.get(key)).isEqualTo("first");
        store.releaseLock(key, "first");
        assertThat(store.get(key)).isNull();
    }

    /** 일반 payload에는 PX가, 세대에는 무기한 수명이 실제 적용되는지 검증한다. */
    @Test
    void payloadHasFiniteTtlWhileGenerationIsPersistent() {
        store.set(namespace + ":data", "payload", Duration.ofSeconds(30));
        store.set(namespace + ":generation", "generation", Duration.ZERO);

        assertThat(connection.sync().pttl(namespace + ":data")).isBetween(1L, 30_000L);
        assertThat(connection.sync().pttl(namespace + ":generation")).isEqualTo(-1L);
    }

    /** 실제 Redis 직렬화 hit와 무효화 후 재집계를 동일 엔진으로 검증한다. */
    @Test
    void realRedisCachesUntilSuccessfulInvalidation() throws Throwable {
        var mapper = JsonMapper.builder().build();
        var properties = new PopularCacheProperties(true, namespace,
                Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofMillis(500),
                Duration.ofSeconds(1));
        var cache = new AggregateResultCache(store, properties, mapper,
                new SimpleMeterRegistry(), Clock.systemUTC());
        var type = mapper.getTypeFactory().constructType(Integer.class);
        var loads = new AtomicInteger();
        AggregateResultCache.Loader loader = () ->
                new AggregateResultCache.Loaded(loads.incrementAndGet(), Long.MAX_VALUE);

        assertThat(cache.get("popular", "1,2", type, loader)).isEqualTo(1);
        assertThat(cache.get("popular", "1,2", type, loader)).isEqualTo(1);
        assertThat(loads).hasValue(1);
        cache.invalidate();
        assertThat(cache.get("popular", "1,2", type, loader)).isEqualTo(2);
        assertThat(loads).hasValue(2);
    }
}
