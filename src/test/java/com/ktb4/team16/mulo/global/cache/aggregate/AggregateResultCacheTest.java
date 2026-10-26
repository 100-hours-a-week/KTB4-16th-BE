package com.ktb4.team16.mulo.global.cache.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

class AggregateResultCacheTest {
    private final MutableClock clock = new MutableClock();
    private final TestAggregateCacheStore store = new TestAggregateCacheStore(clock);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, Region.class);
    private final AggregateCacheProperties properties = enabledProperties(Duration.ofMillis(500));
    private final AggregateResultCache cache = new AggregateResultCache(store, properties, mapper, meters, clock);

    /** 캐시가 동일 요청의 DTO 목록을 복원하고 두 번째 DB 실행을 생략하는지 검증한다. */
    @Test
    void roundTripsTypedListsAndLoadsOnlyOnce() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        assertThat(get("user:1", loads, "서울")).isEqualTo(List.of(new Region("서울", 3L)));
        assertThat(get("user:1", loads, "다른 값")).isEqualTo(List.of(new Region("서울", 3L)));
        assertThat(loads).hasValue(1);
        assertThat(meters.get("mulo.aggregate.cache.requests").tags("cache", "regions", "outcome", "hit")
                .counter().count()).isEqualTo(1);
    }

    /** 사용자별 키와 cache 이름이 다른 응답을 섞지 않는지 검증한다. */
    @Test
    void isolatesRequestKeysAndCacheNames() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("user:1", loads, "첫 사용자");
        assertThat(get("user:2", loads, "둘째 사용자")).isEqualTo(List.of(new Region("둘째 사용자", 3L)));
        Object popular = cache.get("popular", "user:1", type,
                () -> loaded("인기곡", Long.MAX_VALUE));
        assertThat(popular).isEqualTo(List.of(new Region("인기곡", 3L)));
    }

    /** Redis에 만료 키가 남아도 envelope의 경계를 지난 결과를 반환하지 않는지 검증한다. */
    @Test
    void refusesExpiredEnvelopeEvenWhenStoreRetainsIt() throws Throwable {
        store.retainExpiredValues = true;
        cache.get("regions", "boundary", type, () -> loaded("이전 값", clock.millis() + 500));
        clock.advance(Duration.ofMillis(500));
        assertThat(cache.get("regions", "boundary", type, () -> loaded("새 값", Long.MAX_VALUE)))
                .isEqualTo(List.of(new Region("새 값", 3L)));
    }

    /** 설정 TTL보다 오래 유효한 데이터도 설정된 수명 뒤에 다시 로드하는지 검증한다. */
    @Test
    void capsPayloadLifetimeAtConfiguredTtl() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("long-lived", loads, "기존 값");
        clock.advance(Duration.ofSeconds(30));
        get("long-lived", loads, "새 값");
        assertThat(loads).hasValue(2);
    }

    /** 계산이 끝날 때 이미 경계를 넘은 결과는 저장하지 않는지 검증한다. */
    @Test
    void doesNotStoreResultsAlreadyOutsideValidityBoundary() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        AggregateResultCache.Loader expired = () -> {
            loads.incrementAndGet();
            return loaded("만료 결과", clock.millis());
        };
        cache.get("popular", "expired", type, expired);
        cache.get("popular", "expired", type, expired);
        assertThat(loads).hasValue(2);
    }

    /** 쓰기 뒤 세대 변경이 이미 저장된 결과를 더 이상 재사용하지 않게 하는지 검증한다. */
    @Test
    void invalidationMakesPreviouslyCachedPayloadUnreachable() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("user:1", loads, "쓰기 전");
        cache.invalidate();
        assertThat(get("user:1", loads, "쓰기 후")).isEqualTo(List.of(new Region("쓰기 후", 3L)));
        assertThat(loads).hasValue(2);
    }

    /** 로드 중 세대가 바뀌면 늦은 이전 결과를 새 세대에 넣지 않는지 검증한다. */
    @Test
    void doesNotFillNewGenerationWithLoaderStartedBeforeInvalidation() throws Throwable {
        cache.get("regions", "race", type, () -> {
            cache.invalidate();
            return loaded("오래된 snapshot", Long.MAX_VALUE);
        });
        AtomicInteger loads = new AtomicInteger();
        assertThat(get("race", loads, "새 snapshot")).isEqualTo(List.of(new Region("새 snapshot", 3L)));
        get("race", loads, "사용하지 않을 값");
        assertThat(loads).hasValue(1);
    }

    /** 세대 키만 사라져도 이전 payload를 다시 활성화하지 않는지 검증한다. */
    @Test
    void lostGenerationNeverReusesOldPayloadKeys() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("lost-generation", loads, "기존 값");
        store.values.remove(store.generationKey());
        assertThat(get("lost-generation", loads, "새 값")).isEqualTo(List.of(new Region("새 값", 3L)));
        assertThat(loads).hasValue(2);
    }

    /** 프로세스를 새로 시작하면 남아 있는 이전 프로세스의 payload를 신뢰하지 않는지 검증한다. */
    @Test
    void newCacheInstanceRotatesGenerationBeforeItsFirstRead() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("restart", loads, "종료 전 값");
        var restarted = new AggregateResultCache(store, properties, mapper, meters, clock);
        assertThat(restarted.get("regions", "restart", type, () -> loaded("재시작 뒤 값", Long.MAX_VALUE)))
                .isEqualTo(List.of(new Region("재시작 뒤 값", 3L)));
    }

    /** 손상 JSON을 응답하지 않고 DB 결과로 다시 채우는지 검증한다. */
    @Test
    void reloadsCorruptedPayload() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("corrupt", loads, "기존 값");
        store.values.replaceAll((key, value) -> key.contains(":data:")
                ? new TestAggregateCacheStore.Entry("{broken-json", Long.MAX_VALUE) : value);
        assertThat(get("corrupt", loads, "복구 값")).isEqualTo(List.of(new Region("복구 값", 3L)));
        get("corrupt", loads, "사용하지 않을 값");
        assertThat(loads).hasValue(2);
    }

    /** DB 로드 성공 후 Redis 저장 실패가 같은 DB 작업을 반복하지 않는지 검증한다. */
    @Test
    void payloadWriteFailureNeverRunsSuccessfulLoaderTwice() throws Throwable {
        store.rejectPayloadWrites = true;
        AtomicInteger loads = new AtomicInteger();
        assertThat(get("failed-write", loads, "DB 결과")).isEqualTo(List.of(new Region("DB 결과", 3L)));
        assertThat(loads).hasValue(1);
    }

    /** loader 자체 예외를 Redis 장애로 오인하거나 다시 실행하지 않는지 검증한다. */
    @Test
    void propagatesLoaderFailureWithoutRetryingIt() {
        AtomicInteger loads = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("database failed");
        assertThatThrownBy(() -> cache.get("regions", "failed-load", type, () -> {
            loads.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(loads).hasValue(1);
    }

    /** Redis 장애 시 DB로 우회하고 복구 시 이전 키를 신뢰하지 않는지 검증한다. */
    @Test
    void bypassesDuringOutageAndRotatesGenerationAfterRecovery() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        get("outage", loads, "장애 전");
        store.unavailable = true;
        cache.invalidate();
        assertThat(get("outage", loads, "장애 중")).isEqualTo(List.of(new Region("장애 중", 3L)));
        store.unavailable = false;
        assertThat(get("outage", loads, "backoff 중")).isEqualTo(List.of(new Region("backoff 중", 3L)));
        clock.advance(Duration.ofSeconds(1));
        assertThat(get("outage", loads, "복구 후")).isEqualTo(List.of(new Region("복구 후", 3L)));
        get("outage", loads, "사용하지 않을 값");
        assertThat(loads).hasValue(4);
    }

    /** 같은 키로 동시에 시작한 조회들이 하나의 성공한 DB 로드를 공유하는지 검증한다. */
    @Test
    void concurrentColdRequestsShareOneLoad() throws Exception {
        var ready = new CountDownLatch(20);
        var start = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        var shared = new AggregateResultCache(store, enabledProperties(Duration.ofSeconds(2)), mapper, meters, clock);
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return shared.get("regions", "cold", type, () -> {
                            loads.incrementAndGet();
                            Thread.sleep(80);
                            return loaded("공유 값", Long.MAX_VALUE);
                        });
                    } catch (Throwable failure) {
                        throw new IllegalStateException(failure);
                    }
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(List.of(new Region("공유 값", 3L)));
            }
        }
        assertThat(loads).hasValue(1);
    }

    /** 잠금 소유자가 응답하지 않아도 유한 대기 후 DB 결과를 반환하는지 검증한다. */
    @Test
    void busyLockFallsBackAfterFiniteWait() throws Throwable {
        store.busyLocks = true;
        var bounded = new AggregateResultCache(store, enabledProperties(Duration.ofMillis(20)), mapper, meters, clock);
        assertThat(bounded.get("regions", "busy", type, () -> loaded("직접 조회", Long.MAX_VALUE)))
                .isEqualTo(List.of(new Region("직접 조회", 3L)));
        assertThat(meters.get("mulo.aggregate.cache.requests").tags("cache", "regions", "outcome", "bypass")
                .counter().count()).isEqualTo(1);
    }

    /** 만료 후 다른 작업이 얻은 잠금을 이전 작업의 해제가 지우지 않는지 검증한다. */
    @Test
    void oldLoaderDoesNotReleaseSuccessorLock() throws Throwable {
        cache.get("regions", "lease-expired", type, () -> {
            String key = store.values.keySet().stream().filter(value -> value.contains(":lock:"))
                    .findFirst().orElseThrow();
            clock.advance(Duration.ofSeconds(11));
            assertThat(store.putIfAbsent(key, "successor-owner", Duration.ofSeconds(10))).isTrue();
            return loaded("원래 작업", Long.MAX_VALUE);
        });
        assertThat(store.values.entrySet()).anySatisfy(entry -> {
            assertThat(entry.getKey()).contains(":lock:");
            assertThat(entry.getValue().value()).isEqualTo("successor-owner");
        });
    }

    /** 기본 off에서는 Redis 명령이 실패하더라도 DB 경로만 실행되는지 검증한다. */
    @Test
    void disabledCacheNeverUsesRedis() throws Throwable {
        store.unavailable = true;
        var disabled = new AggregateResultCache(store, new AggregateCacheProperties(), mapper, meters, clock);
        assertThat(disabled.get("regions", "off", type, () -> loaded("기존 경로", Long.MAX_VALUE)))
                .isEqualTo(List.of(new Region("기존 경로", 3L)));
        assertThat(store.values).isEmpty();
    }

    /** DB 실행 횟수를 센 뒤 무기한 도메인 유효기간의 지역 응답을 만든다. */
    private Object get(String key, AtomicInteger loads, String name) throws Throwable {
        return cache.get("regions", key, type, () -> {
            loads.incrementAndGet();
            return loaded(name, Long.MAX_VALUE);
        });
    }

    /** 실제 DTO와 경계 시각을 가진 로드 결과를 만든다. */
    private static AggregateResultCache.Loaded loaded(String name, long until) {
        return new AggregateResultCache.Loaded(List.of(new Region(name, 3L)), until);
    }

    /** 테스트마다 필요한 유한 대기만 조정하고 실제 기본 수명을 유지한다. */
    private static AggregateCacheProperties enabledProperties(Duration wait) {
        return new AggregateCacheProperties(true, "127.0.0.1", 16380, "mulo:test", Duration.ofSeconds(30),
                Duration.ofSeconds(10), wait, Duration.ofSeconds(1), Duration.ofMillis(300), Duration.ofMillis(100));
    }

    record Region(String name, Long count) { }

    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-25T00:00:00Z");

        /** 잠금과 TTL 테스트의 가상 시간을 실제 대기 없이 이동한다. */
        void advance(Duration duration) {
            now = now.plus(duration);
        }

        /** 테스트 시계의 시간대는 UTC로 고정한다. */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** UTC 시계를 재사용하며 실제 경과시각을 바꾸지 않는다. */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** 현재 가상 시각을 반환한다. */
        @Override
        public Instant instant() {
            return now;
        }
    }
}
