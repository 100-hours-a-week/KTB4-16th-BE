package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.config.MusicSearchRateLimitProperties;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;

class SpotifySearchRateLimiterFailureTest {
    @Test
    void mapsLazyRedisConnectionFailureToServiceUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StatefulRedisConnection<String, String>> connections = Mockito.mock(ObjectProvider.class);
        when(connections.getObject()).thenThrow(new BeanCreationException("redisConnection",
                new RedisConnectionException("unavailable")));
        SpotifySearchRateLimiter limiter = limiter(connections);

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(MusicProviderUnavailableException.class);
    }

    @Test
    void mapsLuaExecutionFailureToServiceUnavailable() {
        SpotifySearchRateLimiter limiter = limiterWithCommandFailure();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(MusicProviderUnavailableException.class);
    }

    @Test
    void mapsCooldownWriteFailureToServiceUnavailable() {
        SpotifySearchRateLimiter limiter = limiterWithCommandFailure();

        assertThatThrownBy(() -> limiter.block(Duration.ofSeconds(7)))
                .isInstanceOf(MusicProviderUnavailableException.class);
    }

    private SpotifySearchRateLimiter limiterWithCommandFailure() {
        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> connection = Mockito.mock(StatefulRedisConnection.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StatefulRedisConnection<String, String>> connections = Mockito.mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        RedisCommands<String, String> commands = Mockito.mock(RedisCommands.class);
        when(connections.getObject()).thenReturn(connection);
        when(connection.sync()).thenReturn(commands);
        when(commands.eval(Mockito.anyString(), Mockito.any(), Mockito.<String[]>any(),
                Mockito.<String>any())).thenThrow(new RedisConnectionException("unavailable"));
        when(commands.eval(Mockito.anyString(), Mockito.any(), Mockito.<String[]>any(),
                Mockito.<String>any(), Mockito.<String>any()))
                .thenThrow(new RedisConnectionException("unavailable"));
        return limiter(connections);
    }

    private SpotifySearchRateLimiter limiter(
            ObjectProvider<StatefulRedisConnection<String, String>> connections) {
        SpotifyProperties properties = new SpotifyProperties("test-client", "test-secret",
                URI.create("https://accounts.test"), URI.create("https://api.test"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 15, 80);
        return new SpotifySearchRateLimiter(properties,
                new MusicSearchRateLimitProperties("mulo:music-search-rate-limit:test"), connections);
    }
}
