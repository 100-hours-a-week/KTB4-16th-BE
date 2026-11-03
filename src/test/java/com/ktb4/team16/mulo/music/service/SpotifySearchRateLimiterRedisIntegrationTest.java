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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.Mockito;

@EnabledIfEnvironmentVariable(named = "MULO_SPOTIFY_RATE_LIMIT_REDIS_TEST", matches = "true")
class SpotifySearchRateLimiterRedisIntegrationTest {
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private String namespace;

    @BeforeEach
    void connect() {
        client = RedisClient.create("redis://127.0.0.1:16383");
        connection = client.connect();
        namespace = "mulo:music-search-rate-limit:test:spotify-global:" + UUID.randomUUID();
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
    void allowsEightyRequestsAndDoesNotRecordTheEightyFirst() {
        SpotifySearchRateLimiter limiter = limiter();
        String key = requestsKey();

        for (int count = 0; count < 80; count++) {
            limiter.acquire();
        }

        assertThat(connection.sync().zcard(key)).isEqualTo(80);
        assertThatThrownBy(limiter::acquire)
                .isInstanceOfSatisfying(MusicSearchRateLimitedException.class,
                        exception -> assertThat(exception.retryAfter())
                                .isPositive().isLessThanOrEqualTo(Duration.ofSeconds(30)));
        assertThat(connection.sync().zcard(key)).isEqualTo(80);
        assertThat(connection.sync().pttl(key)).isBetween(1L, 31_000L);
    }

    @Test
    void removesRequestsOutsideThirtySecondWindow() {
        String key = requestsKey();
        long now = redisMicros();
        for (int count = 0; count < 80; count++) {
            add(key, now - 30_000_001L, "expired-" + count);
        }

        assertThatCode(limiter()::acquire).doesNotThrowAnyException();

        assertThat(connection.sync().zcard(key)).isEqualTo(1);
    }

    @Test
    void sharesCooldownAndPreservesTheLongestExpiryAcrossInstances() {
        SpotifySearchRateLimiter first = limiter();
        SpotifySearchRateLimiter second = limiter();
        String key = blockedUntilKey();

        first.block(Duration.ofSeconds(2));
        String initialBlockedUntil = connection.sync().get(key);
        second.block(Duration.ofSeconds(7));
        String extendedBlockedUntil = connection.sync().get(key);
        first.block(Duration.ofSeconds(1));

        assertThat(Long.parseLong(extendedBlockedUntil))
                .isGreaterThan(Long.parseLong(initialBlockedUntil));
        assertThat(connection.sync().get(key)).isEqualTo(extendedBlockedUntil);
        long before = redisMicros();
        assertThatThrownBy(second::acquire)
                .isInstanceOfSatisfying(MusicSearchRateLimitedException.class, exception -> {
                    long after = redisMicros();
                    long expiry = Long.parseLong(extendedBlockedUntil);
                    long actual = exception.retryAfter().toNanos() / 1_000;
                    assertThat(actual).isBetween(Math.max(1L, expiry - after - 1_000),
                            expiry - before + 1_000);
                });
    }

    @Test
    void resumesAfterCooldownExpires() throws InterruptedException {
        SpotifySearchRateLimiter limiter = limiter();
        limiter.block(Duration.ofMillis(100));

        Thread.sleep(150);

        assertThatCode(limiter::acquire).doesNotThrowAnyException();
    }

    private SpotifySearchRateLimiter limiter() {
        SpotifyProperties properties = new SpotifyProperties("test-client", "test-secret",
                URI.create("https://accounts.test"), URI.create("https://api.test"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 15, 80);
        @SuppressWarnings("unchecked")
        ObjectProvider<StatefulRedisConnection<String, String>> connections = Mockito.mock(ObjectProvider.class);
        when(connections.getObject()).thenReturn(connection);
        return new SpotifySearchRateLimiter(properties,
                new MusicSearchRateLimitProperties(namespace), connections);
    }

    private long redisMicros() {
        List<String> time = connection.sync().time();
        return Long.parseLong(time.get(0)) * 1_000_000L + Long.parseLong(time.get(1));
    }

    private void add(String key, long score, String member) {
        connection.sync().eval("return redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])",
                ScriptOutputType.INTEGER, new String[] {key}, Long.toString(score), member);
    }

    private String requestsKey() {
        return namespace + ":spotify:global:requests";
    }

    private String blockedUntilKey() {
        return namespace + ":spotify:global:blocked-until";
    }
}
