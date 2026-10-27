package com.ktb4.team16.mulo.global.cache.aggregate;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Redis 없이 TTL·소유권·장애를 재현하는 테스트 전용 저장소다. */
final class TestAggregateCacheStore implements AggregateCacheStore {
    private final Clock clock;
    final Map<String, Entry> values = new ConcurrentHashMap<>();
    volatile boolean unavailable;
    volatile boolean rejectPayloadWrites;
    volatile boolean busyLocks;
    volatile boolean retainExpiredValues;
    volatile BiConsumer<String, String> beforeSet = (key, value) -> { };

    /** 주입한 시계를 Redis의 TTL 기준으로 사용한다. */
    TestAggregateCacheStore(Clock clock) {
        this.clock = clock;
    }

    /** 장애와 만료를 반영해 저장한 문자열을 읽는다. */
    @Override
    public String get(String key) {
        checkAvailable();
        Entry entry = values.get(key);
        if (entry != null && !retainExpiredValues && entry.expiresAt() <= clock.millis()) {
            values.remove(key, entry);
            entry = null;
        }
        return entry == null ? null : entry.value();
    }

    /** 세대와 payload를 구분해 정상 저장 또는 payload 저장 장애를 재현한다. */
    @Override
    public void set(String key, String value, Duration ttl) {
        checkAvailable();
        if (rejectPayloadWrites && key.contains(":data:")) {
            throw new IllegalStateException("simulated payload write failure");
        }
        beforeSet.accept(key, value);
        values.put(key, new Entry(value, expiry(ttl)));
    }

    /** 잠금 경합과 NX의 원자적 신규 저장을 재현한다. */
    @Override
    public synchronized boolean putIfAbsent(String key, String value, Duration ttl) {
        checkAvailable();
        if (busyLocks && key.contains(":lock:")) {
            return false;
        }
        if (get(key) != null) {
            return false;
        }
        values.put(key, new Entry(value, expiry(ttl)));
        return true;
    }

    /** 잠금 토큰이 현재 소유자와 일치할 때만 삭제한다. */
    @Override
    public synchronized void releaseLock(String key, String token) {
        checkAvailable();
        Entry entry = values.get(key);
        if (entry != null && entry.value().equals(token)) {
            values.remove(key, entry);
        }
    }

    /** 특정 suffix를 가진 실제 저장 키를 테스트에서 찾는다. */
    String generationKey() {
        return values.keySet().stream().filter(key -> key.endsWith(":generation"))
                .findFirst().orElseThrow();
    }

    /** 영구 세대와 유한 TTL의 만료시각을 구분한다. */
    private long expiry(Duration ttl) {
        return ttl.isZero() ? Long.MAX_VALUE : clock.millis() + ttl.toMillis();
    }

    /** 장애 중 모든 Redis 명령의 실패를 재현한다. */
    private void checkAvailable() {
        if (unavailable) {
            throw new IllegalStateException("simulated Redis outage");
        }
    }

    record Entry(String value, long expiresAt) { }
}
