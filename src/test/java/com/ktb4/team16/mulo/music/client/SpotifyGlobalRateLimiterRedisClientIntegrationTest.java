package com.ktb4.team16.mulo.music.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.music.config.MusicSearchRateLimitProperties;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import com.ktb4.team16.mulo.music.service.SpotifySearchRateLimiter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.mockito.Mockito;

@EnabledIfEnvironmentVariable(named = "MULO_SPOTIFY_RATE_LIMIT_REDIS_TEST", matches = "true")
class SpotifyGlobalRateLimiterRedisClientIntegrationTest {
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private String namespace;

    @BeforeEach
    void connect() {
        redisClient = RedisClient.create("redis://127.0.0.1:16383");
        connection = redisClient.connect();
        namespace = "mulo:music-search-rate-limit:test:spotify-client:" + UUID.randomUUID();
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
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Test
    void countsUnauthorizedSearchRetryAsAnotherAttempt() {
        SpotifyProperties properties = properties();
        SpotifySearchRateLimiter limiter = limiter(properties);
        String key = requestsKey();
        long now = redisMicros();
        for (int count = 0; count < 79; count++) {
            add(key, now, "initial-" + count);
        }

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SpotifyTokenManager tokenManager = mock(SpotifyTokenManager.class);
        when(tokenManager.getToken()).thenReturn("expired-token", "new-token");
        SpotifyMusicCatalogClient catalog = new SpotifyMusicCatalogClient(builder, properties,
                tokenManager, limiter, new tools.jackson.databind.ObjectMapper());
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> catalog.searchTracks("query"))
                .isInstanceOf(MusicSearchRateLimitedException.class);

        server.verify();
        assertThat(connection.sync().zcard(key)).isEqualTo(80);
    }

    @Test
    void tokenIssueRequestDoesNotConsumeGlobalSearchLimit() {
        SpotifyProperties properties = properties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SpotifyTokenManager tokenManager = new SpotifyTokenManager(builder, properties,
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));
        server.expect(requestTo("https://accounts.test/api/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        assertThat(tokenManager.getToken()).isEqualTo("access-token");

        server.verify();
        assertThat(connection.sync().zcard(requestsKey())).isZero();
    }

    private SpotifySearchRateLimiter limiter(SpotifyProperties properties) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StatefulRedisConnection<String, String>> connections = Mockito.mock(ObjectProvider.class);
        when(connections.getObject()).thenReturn(connection);
        return new SpotifySearchRateLimiter(properties,
                new MusicSearchRateLimitProperties(namespace), connections);
    }

    private SpotifyProperties properties() {
        return new SpotifyProperties("test-client", "test-secret",
                java.net.URI.create("https://accounts.test"), java.net.URI.create("https://api.test"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 15, 80);
    }

    private long redisMicros() {
        List<String> time = connection.sync().time();
        return Long.parseLong(time.get(0)) * 1_000_000L + Long.parseLong(time.get(1));
    }

    private void add(String key, long score, String member) {
        connection.sync().eval("return redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])",
                io.lettuce.core.ScriptOutputType.INTEGER,
                new String[] {key}, Long.toString(score), member);
    }

    private String requestsKey() {
        return namespace + ":spotify:global:requests";
    }
}
