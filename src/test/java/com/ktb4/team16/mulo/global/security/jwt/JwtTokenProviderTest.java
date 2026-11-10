package com.ktb4.team16.mulo.global.security.jwt;

import com.ktb4.team16.mulo.global.config.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private static final JwtProperties TEST_PROPERTIES = new JwtProperties(
            Base64.getEncoder().encodeToString(new byte[32]), Duration.ofHours(1), Duration.ofDays(7));
    private final JwtTokenProvider provider = new JwtTokenProvider(TEST_PROPERTIES);

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

    @Test
    void sessionAwareTokensUseAnExplicitSessionIdClaim() {
        String accessToken = provider.createAccessToken(42L, "session-42");
        String refreshToken = provider.createRefreshToken(42L, "session-42");
        var decoder = NimbusJwtDecoder.withSecretKey(TEST_PROPERTIES.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        assertThat(decoder.decode(accessToken).getClaims())
                .containsEntry("sessionId", "session-42")
                .doesNotContainKey("sid");
        assertThat(decoder.decode(refreshToken).getClaims())
                .containsEntry("sessionId", "session-42")
                .doesNotContainKey("sid");
    }

    /** 만료된 서명 유효 Refresh Token도 로그아웃용 세션 정보로 읽되 일반 refresh 검증은 거부하는지 확인한다. */
    @Test
    void expiredRefreshTokenClaimsCanBeReadForLogoutOnly() {
        String token = createExpiredRefreshToken(42L, "expired-session");

        assertThatThrownBy(() -> provider.extractRefreshUserId(token))
                .isInstanceOf(JwtException.class);
        assertThat(provider.extractRefreshTokenDetailsForLogout(token))
                .isEqualTo(new JwtTokenProvider.RefreshTokenDetails(42L, "expired-session"));
    }

    /** nbf가 미래인 세션은 만료 토큰이어도 로그아웃 claim으로 인정하지 않는다. */
    @Test
    void refreshTokenThatIsNotYetValidCannotBeReadForLogout() {
        String token = createRefreshTokenWithNotBefore(42L, "future-session", Instant.now().plus(Duration.ofHours(2)));

        assertThatThrownBy(() -> provider.extractRefreshTokenDetailsForLogout(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("not active yet");
    }

    /** 테스트용 키로 만료 시각이 지난 Refresh JWT를 서명해 만든다. */
    private String createExpiredRefreshToken(Long userId, String sessionId) {
        Instant expiredAt = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(expiredAt.minus(Duration.ofDays(1)))
                .expiresAt(expiredAt)
                .claim("tokenType", "refresh")
                .claim("sessionId", sessionId)
                .build();
        var encoder = NimbusJwtEncoder.withSecretKey(TEST_PROPERTIES.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    /** nbf 경계 검증에 사용할 서명된 Refresh JWT를 만든다. */
    private String createRefreshTokenWithNotBefore(Long userId, String sessionId, Instant notBefore) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .notBefore(notBefore)
                .expiresAt(now.plus(Duration.ofDays(1)))
                .claim("tokenType", "refresh")
                .claim("sessionId", sessionId)
                .build();
        var encoder = NimbusJwtEncoder.withSecretKey(TEST_PROPERTIES.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }
}
