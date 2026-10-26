package com.ktb4.team16.mulo.global.cache.aggregate;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/** DB 로드 바깥에서 세대·유효시각·유한 잠금을 적용하는 집계 캐시다. */
public class AggregateResultCache {
    private static final long POLL_NANOS = Duration.ofMillis(10).toNanos();
    private final AggregateCacheStore store;
    private final PopularCacheProperties properties;
    private final JsonMapper mapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Object recoveryMonitor = new Object();
    private volatile boolean rotateBeforeRead = true;
    private volatile long retryAfterMillis;

    /** 캐시 경계의 저장소·설정·직렬화·계측·시계를 주입한다. */
    public AggregateResultCache(AggregateCacheStore store, PopularCacheProperties properties,
            JsonMapper mapper, MeterRegistry meters, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.mapper = mapper;
        this.meters = meters;
        this.clock = clock;
    }

    /** 캐시 hit 또는 잠금 소유자의 단일 DB 로드로 요청을 처리한다. */
    public Object get(String cacheName, String requestKey, JavaType type, Loader loader) throws Throwable {
        Lookup lookup = prepare(cacheName, requestKey, type);
        if (lookup.found()) {
            return lookup.value();
        }
        if (lookup.lockToken() == null) {
            return load(cacheName, loader).value();
        }
        try {
            // loader 예외는 Redis 장애 처리와 분리하여 DB 작업을 두 번 실행하지 않는다.
            Loaded loaded = load(cacheName, loader);
            save(cacheName, lookup, loaded);
            return loaded.value();
        } finally {
            release(cacheName, lookup.lockKey(), lookup.lockToken());
        }
    }

    /** 완료된 쓰기의 전역 세대를 바꾸며 Redis 실패는 이미 성공한 쓰기로 전파하지 않는다. */
    public void invalidate() {
        if (!properties.enabled()) {
            return;
        }
        synchronized (recoveryMonitor) {
            rotateBeforeRead = true;
            if (clock.millis() < retryAfterMillis) {
                return;
            }
            try {
                store.set(generationKey(), UUID.randomUUID().toString(), Duration.ZERO);
                rotateBeforeRead = false;
                count("global", "invalidate");
            } catch (RuntimeException failure) {
                failed("global");
            }
        }
    }

    /** Redis 처리에만 장애 회로를 적용하고 loader 호출 전 조회·잠금 상태를 정한다. */
    private Lookup prepare(String cacheName, String requestKey, JavaType type) {
        if (!properties.enabled() || clock.millis() < retryAfterMillis) {
            return bypass(cacheName);
        }
        try {
            String generation = generation();
            String suffix = generation + ":" + cacheName + ":" + digest(requestKey);
            String key = properties.namespace() + ":data:" + suffix;
            String lockKey = properties.namespace() + ":lock:" + suffix;
            Lookup hit = read(cacheName, generation, key, type);
            if (hit != null) {
                return hit;
            }
            count(cacheName, "miss");
            String token = UUID.randomUUID().toString();
            if (store.putIfAbsent(lockKey, token, properties.lockTtl())) {
                return owned(cacheName, generation, key, lockKey, token, type);
            }
            count(cacheName, "wait");
            long started = System.nanoTime();
            while (System.nanoTime() - started < properties.waitTimeout().toNanos()
                    && !Thread.currentThread().isInterrupted()) {
                if (!generation.equals(store.get(generationKey()))) {
                    return bypass(cacheName);
                }
                hit = read(cacheName, generation, key, type);
                if (hit != null) {
                    return hit;
                }
                if (store.putIfAbsent(lockKey, token, properties.lockTtl())) {
                    return owned(cacheName, generation, key, lockKey, token, type);
                }
                long remaining = properties.waitTimeout().toNanos() - (System.nanoTime() - started);
                LockSupport.parkNanos(Math.min(POLL_NANOS, Math.max(0, remaining)));
            }
            return bypass(cacheName);
        } catch (RuntimeException failure) {
            failed(cacheName);
            return bypass(cacheName);
        }
    }

    /** 잠금을 얻은 직후 다른 소유자가 남긴 결과와 세대를 재확인한다. */
    private Lookup owned(String cacheName, String generation, String key, String lockKey,
            String token, JavaType type) {
        try {
            Lookup hit = read(cacheName, generation, key, type);
            if (hit != null) {
                release(cacheName, lockKey, token);
                return hit;
            }
            if (!generation.equals(store.get(generationKey()))) {
                release(cacheName, lockKey, token);
                return bypass(cacheName);
            }
            return new Lookup(false, null, generation, key, lockKey, token);
        } catch (RuntimeException failure) {
            release(cacheName, lockKey, token);
            throw failure;
        }
    }

    /** 저장된 envelope를 실제 응답 타입으로 복원하고 시간·세대가 유효할 때만 반환한다. */
    private Lookup read(String cacheName, String generation, String key, JavaType type) {
        String json = store.get(key);
        if (json == null) {
            return null;
        }
        Envelope<?> envelope;
        try {
            JavaType envelopeType = mapper.getTypeFactory().constructParametricType(Envelope.class, type);
            envelope = mapper.readValue(json, envelopeType);
        } catch (RuntimeException malformed) {
            count(cacheName, "corrupt");
            return null;
        }
        if (envelope == null || envelope.validUntilEpochMilli() <= clock.millis()) {
            return null;
        }
        if (!generation.equals(store.get(generationKey()))) {
            return null;
        }
        count(cacheName, "hit");
        return new Lookup(true, envelope.payload(), null, null, null, null);
    }

    /** 로더를 정확히 한 번 실행하고 DB 로드 횟수를 요청 키 없이 계측한다. */
    private Loaded load(String cacheName, Loader loader) throws Throwable {
        count(cacheName, "load");
        return loader.load();
    }

    /** DB 로드 당시 세대에만 설정 TTL과 도메인 유효기간의 최솟값으로 저장한다. */
    private void save(String cacheName, Lookup lookup, Loaded loaded) {
        long now = clock.millis();
        long until = Math.min(loaded.validUntilEpochMilli(), now + properties.ttl().toMillis());
        if (until <= now) {
            count(cacheName, "expired");
            return;
        }
        try {
            if (!lookup.generation().equals(store.get(generationKey()))) {
                count(cacheName, "generation_changed");
                return;
            }
            String json = mapper.writeValueAsString(new Envelope<>(loaded.value(), until));
            store.set(lookup.key(), json, Duration.ofMillis(until - now));
        } catch (RuntimeException failure) {
            failed(cacheName);
        }
    }

    /** 시작·장애 복구 시 새 세대를 만들고 세대 키 유실 시 UUID를 원자적으로 보충한다. */
    private String generation() {
        if (rotateBeforeRead) {
            synchronized (recoveryMonitor) {
                if (rotateBeforeRead) {
                    store.set(generationKey(), UUID.randomUUID().toString(), Duration.ZERO);
                    rotateBeforeRead = false;
                }
            }
        }
        String current = store.get(generationKey());
        if (current == null) {
            store.putIfAbsent(generationKey(), UUID.randomUUID().toString(), Duration.ZERO);
            current = store.get(generationKey());
        }
        if (current == null) {
            throw new IllegalStateException("Redis aggregate generation is unavailable");
        }
        return current;
    }

    /** 잠금 해제 실패도 DB 결과나 이미 성공한 쓰기의 예외로 바꾸지 않는다. */
    private void release(String cacheName, String key, String token) {
        try {
            store.releaseLock(key, token);
        } catch (RuntimeException failure) {
            failed(cacheName);
        }
    }

    /** Redis 오류 뒤 재시도까지 DB를 사용하고 복구 후 세대 회전을 요구한다. */
    private void failed(String cacheName) {
        rotateBeforeRead = true;
        retryAfterMillis = clock.millis() + properties.retryBackoff().toMillis();
        count(cacheName, "error");
    }

    /** DB 우회 상태를 반환하고 원인을 유한한 outcome 태그로 계측한다. */
    private Lookup bypass(String cacheName) {
        count(cacheName, "bypass");
        return new Lookup(false, null, null, null, null, null);
    }

    /** 개별 사용자·요청 키 없이 캐시 종류와 처리 결과만 기록한다. */
    private void count(String cacheName, String outcome) {
        meters.counter("mulo.aggregate.cache.requests", "cache", cacheName, "outcome", outcome).increment();
    }

    /** 모든 API 결과를 함께 무효화할 영구 세대 키를 구성한다. */
    private String generationKey() {
        return properties.namespace() + ":generation";
    }

    /** 사용자·장소 키의 길이와 구분자 영향을 제한하는 SHA-256 식별자를 만든다. */
    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required by the JDK", failure);
        }
    }

    @FunctionalInterface
    public interface Loader {
        /** 실제 DB 집계와 도메인 경계시각을 함께 읽는다. */
        Loaded load() throws Throwable;
    }

    public record Loaded(Object value, long validUntilEpochMilli) { }

    public record Envelope<T>(T payload, long validUntilEpochMilli) { }

    private record Lookup(boolean found, Object value, String generation, String key,
            String lockKey, String lockToken) { }
}
