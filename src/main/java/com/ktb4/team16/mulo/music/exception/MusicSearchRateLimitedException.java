package com.ktb4.team16.mulo.music.exception;

import java.time.Duration;

public class MusicSearchRateLimitedException extends RuntimeException {
    private final Duration retryAfter;

    // 클라이언트가 다시 요청할 수 있는 대기 시간을 예외에 보존한다.
    public MusicSearchRateLimitedException(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            throw new IllegalArgumentException("Retry-After must be positive");
        }
        this.retryAfter = retryAfter;
    }

    // HTTP Retry-After 헤더로 변환할 대기 시간을 반환한다.
    public Duration retryAfter() {
        return retryAfter;
    }
}
