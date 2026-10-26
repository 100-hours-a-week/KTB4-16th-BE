package com.ktb4.team16.mulo.global.cache.aggregate;

import java.time.Duration;

/** 집계 캐시가 사용하는 Redis 문자열·세대·소유권 잠금의 최소 계약이다. */
public interface AggregateCacheStore {
    /** 키의 문자열을 읽고 키가 없으면 null을 반환한다. */
    String get(String key);

    /** 문자열을 저장하며 ZERO TTL은 영구 세대에만 사용한다. */
    void set(String key, String value, Duration ttl);

    /** 키가 없을 때만 TTL과 함께 문자열을 원자적으로 저장한다. */
    boolean putIfAbsent(String key, String value, Duration ttl);

    /** 현재 토큰의 소유자일 때만 잠금을 원자적으로 해제한다. */
    void releaseLock(String key, String token);
}
