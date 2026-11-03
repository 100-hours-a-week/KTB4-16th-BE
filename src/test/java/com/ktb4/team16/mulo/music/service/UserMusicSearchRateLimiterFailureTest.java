package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import com.ktb4.team16.mulo.music.config.MusicSearchRateLimitProperties;
import com.ktb4.team16.mulo.music.config.SpotifyConfiguration;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class UserMusicSearchRateLimiterFailureTest {
    @Test
    void mapsRedisFailureToServiceUnavailablePath() {
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
        UserMusicSearchRateLimiter limiter = new UserMusicSearchRateLimiter(Clock.systemUTC(),
                properties(), new MusicSearchRateLimitProperties("test"), connections);

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(MusicProviderUnavailableException.class);
        verify(connections).getObject();
    }

    @Test
    void resolvesRedisConnectionOnlyWhenRateLimitIsAcquired() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StatefulRedisConnection<String, String>> connections = Mockito.mock(ObjectProvider.class);
        UserMusicSearchRateLimiter limiter = new UserMusicSearchRateLimiter(Clock.systemUTC(),
                properties(), new MusicSearchRateLimitProperties("test"), connections);

        verifyNoInteractions(connections);

        when(connections.getObject()).thenThrow(new BeanCreationException("redisConnection",
                new RedisConnectionException("unavailable")));

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(MusicProviderUnavailableException.class);
        verify(connections).getObject();
    }

    @Test
    void createsLimiterBeanWithoutConnectingToUnavailableRedis() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisConfiguration.class, SpotifyConfiguration.class,
                        LimiterTestConfiguration.class)
                .withPropertyValues(
                        "mulo.redis.host=127.0.0.1",
                        "mulo.redis.port=1",
                        "mulo.music-search-rate-limit.namespace=mulo:music-search-rate-limit:test",
                        "spotify.client-id=test-client",
                        "spotify.client-secret=test-secret",
                        "spotify.accounts-base-url=https://accounts.test",
                        "spotify.api-base-url=https://api.test",
                        "spotify.connect-timeout=PT1S",
                        "spotify.read-timeout=PT1S",
                        "spotify.user-limit-30-seconds=8",
                        "spotify.user-limit-60-seconds=15",
                        "spotify.global-limit-30-seconds=80")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    context.getBean(UserMusicSearchRateLimiter.class);
                    assertThat(context.getBeanFactory().containsSingleton("redisClient")).isFalse();
                    assertThat(context.getBeanFactory().containsSingleton("redisConnection")).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(UserMusicSearchRateLimiter.class)
    static class LimiterTestConfiguration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private SpotifyProperties properties() {
        return new SpotifyProperties("test-client", "test-secret", URI.create("https://accounts.test"),
                URI.create("https://api.test"), Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 15, 80);
    }
}
