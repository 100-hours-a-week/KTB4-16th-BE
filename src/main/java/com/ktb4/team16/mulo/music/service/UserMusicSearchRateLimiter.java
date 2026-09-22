package com.ktb4.team16.mulo.music.service;

import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserMusicSearchRateLimiter {
    private static final Duration SHORT_WINDOW = Duration.ofSeconds(30);
    private static final Duration LONG_WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final int shortLimit;
    private final int longLimit;
    private final Map<Long, ArrayDeque<Instant>> requestsByUser = new HashMap<>();
    private Instant nextCleanupAt = Instant.EPOCH;

    // 환경 설정의 사용자별 한도로 운영 RateLimiter를 생성한다.
    @Autowired
    public UserMusicSearchRateLimiter(Clock clock, SpotifyProperties properties) {
        this(clock, properties.userLimit30Seconds(), properties.userLimit60Seconds());
    }

    // 테스트가 고정 시간과 한도값을 주입할 수 있게 한다.
    UserMusicSearchRateLimiter(Clock clock, int shortLimit, int longLimit) {
        this.clock = clock;
        this.shortLimit = shortLimit;
        this.longLimit = longLimit;
    }

    // 사용자의 30초·1분 창을 동시에 검사하고 허용된 호출을 기록한다.
    public synchronized void acquire(Long userId) {
        Instant now = clock.instant();
        evictIdleUsers(now);
        ArrayDeque<Instant> requests = requestsByUser.computeIfAbsent(
                userId, ignored -> new ArrayDeque<>());
        removeExpired(requests, now.minus(LONG_WINDOW));

        long recentCount = requests.stream()
                .filter(requestAt -> requestAt.isAfter(now.minus(SHORT_WINDOW)))
                .count();
        Duration retryAfter = Duration.ZERO;
        if (recentCount >= shortLimit) {
            Instant oldestRecent = requests.stream()
                    .filter(requestAt -> requestAt.isAfter(now.minus(SHORT_WINDOW)))
                    .findFirst()
                    .orElse(now);
            retryAfter = Duration.between(now, oldestRecent.plus(SHORT_WINDOW));
        }
        if (requests.size() >= longLimit) {
            Duration longRetry = Duration.between(now,
                    requests.getFirst().plus(LONG_WINDOW));
            if (longRetry.compareTo(retryAfter) > 0) {
                retryAfter = longRetry;
            }
        }
        if (!retryAfter.isZero() && !retryAfter.isNegative()) {
            throw new MusicSearchRateLimitedException(retryAfter);
        }
        requests.addLast(now);
    }

    // 1분마다 모든 사용자 기록을 훑어 긴 창 밖의 빈 사용자 항목을 제거한다.
    private void evictIdleUsers(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }
        Instant cutoff = now.minus(LONG_WINDOW);
        requestsByUser.entrySet().removeIf(entry -> {
            removeExpired(entry.getValue(), cutoff);
            return entry.getValue().isEmpty();
        });
        nextCleanupAt = now.plus(LONG_WINDOW);
    }

    // 1분 창 밖으로 벗어난 호출 기록을 제거한다.
    private void removeExpired(ArrayDeque<Instant> requests, Instant cutoff) {
        while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff)) {
            requests.removeFirst();
        }
    }
}
