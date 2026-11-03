package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.config.MusicSearchRateLimitProperties;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.Mockito;

@EnabledIfEnvironmentVariable(named = "MULO_SPOTIFY_RATE_LIMIT_REDIS_TEST", matches = "true")
class UserMusicSearchRateLimiterRedisIntegrationTest {
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private String namespace;

    @BeforeEach
    void connect() {
        client = RedisClient.create("redis://127.0.0.1:16383");
        connection = client.connect();
        namespace = "mulo:music-search-rate-limit:test:" + UUID.randomUUID();
    }

    @AfterEach
    void close() {
        if (connection != null) {
            List<String> keys = connection.sync().keys(namespace + "*");
            if (!keys.isEmpty()) {
                connection.sync().del(keys.toArray(String[]::new));
            }
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void allowsEightRequestsAndRejectsTheNinthInsideThirtySeconds() {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);

        for (int count = 0; count < 8; count++) {
            limiter.acquire(1L);
        }

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(MusicSearchRateLimitedException.class);
    }

    @Test
    void allowsRequestAfterThirtySecondBoundary() {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);
        String key = key(1L);
        long now = redisMicros();
        for (int count = 0; count < 8; count++) {
            add(key, now - 30_000_001L, "expired-short-" + count);
        }

        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSixteenthRequestInsideLongWindow() {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);
        String key = key(1L);
        long now = redisMicros();
        for (int count = 0; count < 15; count++) {
            add(key, now - 31_000_000L, "long-window-" + count);
        }

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(MusicSearchRateLimitedException.class);
    }

    @Test
    void returnsTheLongerRetryAfterWhenBothWindowsAreLimited() {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);
        long now = redisMicros();
        for (int count = 0; count < 7; count++) {
            add(key(1L), now - 31_000_000L, "long-" + count);
        }
        for (int count = 0; count < 8; count++) {
            add(key(1L), now - 29_000_000L, "short-" + count);
        }

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOfSatisfying(MusicSearchRateLimitedException.class,
                        exception -> assertThat(exception.retryAfter()).isGreaterThan(Duration.ofSeconds(25)));
    }

    @Test
    void keepsUsersIndependent() {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);
        for (int count = 0; count < 8; count++) {
            limiter.acquire(1L);
        }

        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void isolatesSameUserAcrossNamespaces() {
        UserMusicSearchRateLimiter devLimiter = limiter(8, 15, namespace + ":dev");
        UserMusicSearchRateLimiter stagingLimiter = limiter(8, 15, namespace + ":staging");

        for (int count = 0; count < 8; count++) {
            devLimiter.acquire(1L);
            stagingLimiter.acquire(1L);
        }

        assertThatThrownBy(() -> devLimiter.acquire(1L))
                .isInstanceOf(MusicSearchRateLimitedException.class);
        assertThatThrownBy(() -> stagingLimiter.acquire(1L))
                .isInstanceOf(MusicSearchRateLimitedException.class);
    }

    @Test
    void limitsConcurrentRequestsAtomically() throws InterruptedException {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            for (int count = 0; count < 20; count++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        limiter.acquire(1L);
                        allowed.incrementAndGet();
                    } catch (MusicSearchRateLimitedException exception) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(allowed).hasValue(8);
        assertThat(rejected).hasValue(12);
    }

    @Test
    void removesExpiredEntriesAndSetsTtl() {
        UserMusicSearchRateLimiter limiter = limiter(8, 15);
        String key = key(1L);
        add(key, redisMicros() - 60_000_001L, "expired-long");

        limiter.acquire(1L);

        assertThat(connection.sync().zcard(key)).isEqualTo(1);
        assertThat(connection.sync().pttl(key)).isBetween(1L, 61_000L);
    }

    private UserMusicSearchRateLimiter limiter(int shortLimit, int longLimit) {
        return limiter(shortLimit, longLimit, namespace);
    }

    private UserMusicSearchRateLimiter limiter(int shortLimit, int longLimit, String limiterNamespace) {
        SpotifyProperties properties = new SpotifyProperties("test-client", "test-secret",
                URI.create("https://accounts.test"), URI.create("https://api.test"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), shortLimit, longLimit, 80);
        @SuppressWarnings("unchecked")
        ObjectProvider<StatefulRedisConnection<String, String>> connections = Mockito.mock(ObjectProvider.class);
        when(connections.getObject()).thenReturn(connection);
        return new UserMusicSearchRateLimiter(Clock.systemUTC(), properties,
                new MusicSearchRateLimitProperties(limiterNamespace), connections);
    }

    private long redisMicros() {
        List<String> time = connection.sync().time();
        return Long.parseLong(time.get(0)) * 1_000_000L + Long.parseLong(time.get(1));
    }

    private void add(String key, long score, String member) {
        connection.sync().eval("return redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])",
                ScriptOutputType.INTEGER, new String[] {key}, Long.toString(score), member);
    }

    private String key(Long userId) {
        return namespace + ":user:" + userId;
    }
}
