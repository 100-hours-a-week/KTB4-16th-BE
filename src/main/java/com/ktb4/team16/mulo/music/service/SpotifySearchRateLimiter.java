package com.ktb4.team16.mulo.music.service;

import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpotifySearchRateLimiter {
    private static final Duration WINDOW = Duration.ofSeconds(30);

    private final Clock clock;
    private final int limit;
    private final ArrayDeque<Instant> requests = new ArrayDeque<>();
    private Instant blockedUntil = Instant.EPOCH;

    // 환경 설정의 앱 전체 호출 한도로 운영 RateLimiter를 생성한다.
    @Autowired
    public SpotifySearchRateLimiter(Clock clock, SpotifyProperties properties) {
        this(clock, properties.globalLimit30Seconds());
    }

    // 테스트가 고정 시간과 한도값을 주입할 수 있게 한다.
    SpotifySearchRateLimiter(Clock clock, int limit) {
        this.clock = clock;
        this.limit = limit;
    }

    // Spotify 30초 호출 창과 현재 429 차단 시간을 검사한다.
    public synchronized void acquire() {
        Instant now = clock.instant();
        if (now.isBefore(blockedUntil)) {
            throw new MusicSearchRateLimitedException(Duration.between(now, blockedUntil));
        }
        Instant cutoff = now.minus(WINDOW);
        while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff)) {
            requests.removeFirst();
        }
        if (requests.size() >= limit) {
            throw new MusicSearchRateLimitedException(
                    Duration.between(now, requests.getFirst().plus(WINDOW)));
        }
        requests.addLast(now);
    }

    // Spotify Retry-After가 끝날 때까지 모든 검색 호출을 차단한다.
    public synchronized void block(Duration retryAfter) {
        Instant candidate = clock.instant().plus(retryAfter);
        if (candidate.isAfter(blockedUntil)) {
            blockedUntil = candidate;
        }
    }
}
