package com.ktb4.team16.mulo.global.cache.aggregate;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 명시적으로 켠 로컬 인기곡 캐시의 주소·유한 대기·수명을 바인딩한다. */
@ConfigurationProperties("mulo.aggregate-cache")
public record AggregateCacheProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("127.0.0.1") String host,
        @DefaultValue("16382") int port,
        @DefaultValue("mulo:popular-cache-local") String namespace,
        @DefaultValue("30s") Duration ttl,
        @DefaultValue("10s") Duration lockTtl,
        @DefaultValue("500ms") Duration waitTimeout,
        @DefaultValue("1s") Duration retryBackoff,
        @DefaultValue("300ms") Duration connectTimeout,
        @DefaultValue("100ms") Duration commandTimeout) {

    /** 여러 생성자 중 환경 설정을 받을 표준 생성자를 명시한다. */
    @ConstructorBinding
    public AggregateCacheProperties {
    }

    /** 직접 생성하는 테스트에서도 실행 프로필과 같은 안전한 기본값을 사용한다. */
    public AggregateCacheProperties() {
        this(false, "127.0.0.1", 16382, "mulo:popular-cache-local", Duration.ofSeconds(30),
                Duration.ofSeconds(10), Duration.ofMillis(500), Duration.ofSeconds(1),
                Duration.ofMillis(300), Duration.ofMillis(100));
    }
}
