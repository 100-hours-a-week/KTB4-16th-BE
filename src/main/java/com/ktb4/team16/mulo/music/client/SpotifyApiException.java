package com.ktb4.team16.mulo.music.client;

import java.time.Duration;

public class SpotifyApiException extends RuntimeException {
    public enum Kind {
        RATE_LIMITED,
        UNAVAILABLE
    }

    private final Kind kind;
    private final Duration retryAfter;

    // 공급자 오류의 분류와 선택적 대기시간을 내부에 보존한다.
    private SpotifyApiException(Kind kind, Duration retryAfter, Throwable cause) {
        super(cause);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    // Spotify 429를 대기 가능한 내부 오류로 생성한다.
    public static SpotifyApiException rateLimited(Duration retryAfter) {
        return new SpotifyApiException(Kind.RATE_LIMITED, retryAfter, null);
    }

    // 복구 시각을 모르는 Spotify 장애를 내부 오류로 생성한다.
    public static SpotifyApiException unavailable(Throwable cause) {
        return new SpotifyApiException(Kind.UNAVAILABLE, null, cause);
    }

    // 서비스 계층이 분기할 공급자 오류 종류를 반환한다.
    public Kind kind() {
        return kind;
    }

    // 429 응답에서 전달받은 대기시간을 반환한다.
    public Duration retryAfter() {
        return retryAfter;
    }
}
