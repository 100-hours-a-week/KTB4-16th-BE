package com.ktb4.team16.mulo.music.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spotify")
public record SpotifyProperties(
        String clientId,
        String clientSecret,
        URI accountsBaseUrl,
        URI apiBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int userLimit30Seconds,
        int userLimit60Seconds,
        int globalLimit30Seconds
) {
    // Spotify 연동에 필요한 필수 설정과 양수 제한값을 애플리케이션 시작 시 검증한다.
    public SpotifyProperties {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "Spotify client credentials must be configured");
        }
        if (accountsBaseUrl == null || apiBaseUrl == null) {
            throw new IllegalArgumentException("Spotify base URLs must be configured");
        }
        if (connectTimeout == null || connectTimeout.isNegative()
                || connectTimeout.isZero() || readTimeout == null
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Spotify timeouts must be positive");
        }
        if (userLimit30Seconds <= 0 || userLimit60Seconds <= 0
                || globalLimit30Seconds <= 0) {
            throw new IllegalArgumentException("Spotify rate limits must be positive");
        }
    }
}
