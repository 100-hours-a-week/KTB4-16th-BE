package com.ktb4.team16.mulo.global.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 기능별 캐시 정책과 독립적으로 Redis 주소와 연결 제한시간만 관리한다. */
@ConfigurationProperties("mulo.redis")
public record RedisConnectionProperties(
        @DefaultValue("127.0.0.1") String host,
        @DefaultValue("16382") int port,
        @DefaultValue("300ms") Duration connectTimeout,
        @DefaultValue("100ms") Duration commandTimeout) {
}
