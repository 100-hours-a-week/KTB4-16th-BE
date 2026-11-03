package com.ktb4.team16.mulo.global.cache.aggregate;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 공통 Redis 접속 정보와 분리된 인기곡 캐시의 활성화·키·수명·대기 정책이다. */
@ConfigurationProperties("mulo.popular-cache")
public record PopularCacheProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("mulo:popular-cache-local") String namespace,
        @DefaultValue("30s") Duration ttl,
        @DefaultValue("10s") Duration lockTtl,
        @DefaultValue("500ms") Duration waitTimeout,
        @DefaultValue("1s") Duration retryBackoff) {

    /** 환경 설정을 받을 표준 생성자를 명시한다. */
    @ConstructorBinding
    public PopularCacheProperties {
    }

    /** 직접 생성하는 테스트에서도 비활성화와 기존 캐시 정책을 기본으로 사용한다. */
    public PopularCacheProperties() {
        this(false, "mulo:popular-cache-local", Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofMillis(500), Duration.ofSeconds(1));
    }
}
