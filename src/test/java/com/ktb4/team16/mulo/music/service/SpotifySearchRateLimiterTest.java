package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SpotifySearchRateLimiterTest {
    @Test
    void rejectsTheEightyFirstCallInsideThirtySeconds() {
        SpotifySearchRateLimiter limiter = new SpotifySearchRateLimiter(
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 80);
        for (int count = 0; count < 80; count++) {
            limiter.acquire();
        }

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(MusicSearchRateLimitedException.class);
    }

    @Test
    void blocksCallsDuringSpotifyRetryAfter() {
        SpotifySearchRateLimiter limiter = new SpotifySearchRateLimiter(
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 80);
        limiter.block(Duration.ofSeconds(7));

        assertThatThrownBy(limiter::acquire)
                .isInstanceOfSatisfying(MusicSearchRateLimitedException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(
                                exception.retryAfter()).isEqualTo(Duration.ofSeconds(7)));
    }
}
