package com.ktb4.team16.mulo.global.security.jwt;

import com.ktb4.team16.mulo.global.config.JwtProperties;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(
            Base64.getEncoder().encodeToString(new byte[32]), Duration.ofHours(1), Duration.ofDays(7)));

    @Test
    void accessTokenContainsUserIdAndAccessType() {
        String token = provider.createAccessToken(42L);

        assertThat(provider.extractAccessUserId(token)).isEqualTo(42L);
    }

    @Test
    void refreshTokenCannotBeReadAsAccessToken() {
        String refreshToken = provider.createRefreshToken(42L);

        assertThatThrownBy(() -> provider.extractAccessUserId(refreshToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokenContainsUserIdAndRefreshType() {
        String token = provider.createRefreshToken(42L);

        assertThat(provider.extractRefreshUserId(token)).isEqualTo(42L);
    }

    @Test
    void accessTokenCannotBeReadAsRefreshToken() {
        String accessToken = provider.createAccessToken(42L);

        assertThatThrownBy(() -> provider.extractRefreshUserId(accessToken))
                .isInstanceOf(JwtException.class);
    }
}
