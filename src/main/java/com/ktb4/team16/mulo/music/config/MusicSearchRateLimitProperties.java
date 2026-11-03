package com.ktb4.team16.mulo.music.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mulo.music-search-rate-limit")
public record MusicSearchRateLimitProperties(String namespace) {
    public MusicSearchRateLimitProperties {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("음악 검색 요청 제한의 namespace가 설정되지 않았습니다.");
        }
    }
}
