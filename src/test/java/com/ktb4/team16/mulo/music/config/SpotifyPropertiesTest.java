package com.ktb4.team16.mulo.music.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SpotifyPropertiesTest {
    @Test
    void rejectsBlankClientSecret() {
        assertThatThrownBy(() -> properties("client-id", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spotify client credentials must be configured");
    }

    @Test
    void rejectsNonPositiveRateLimit() {
        assertThatThrownBy(() -> new SpotifyProperties(
                "client-id", "client-secret",
                URI.create("https://accounts.spotify.com"),
                URI.create("https://api.spotify.com"),
                Duration.ofSeconds(2), Duration.ofSeconds(3),
                0, 15, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spotify rate limits must be positive");
    }

    private SpotifyProperties properties(String clientId, String clientSecret) {
        return new SpotifyProperties(
                clientId, clientSecret,
                URI.create("https://accounts.spotify.com"),
                URI.create("https://api.spotify.com"),
                Duration.ofSeconds(2), Duration.ofSeconds(3),
                8, 15, 80);
    }
}
