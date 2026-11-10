package com.ktb4.team16.mulo.global.security.jwt;

import com.ktb4.team16.mulo.global.config.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final Duration LOGOUT_NOT_BEFORE_CLOCK_SKEW = Duration.ofSeconds(60);
    private static final String SESSION_ID_CLAIM = "sessionId";

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtDecoder logoutDecoder;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.encoder = NimbusJwtEncoder.withSecretKey(properties.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
        this.decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        NimbusJwtDecoder expiredTokenDecoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 로그아웃은 exp가 지난 세션도 차단해야 하므로 서명 검증 후 시간 claim 검사만 생략한다.
        expiredTokenDecoder.setJwtValidator(jwt -> {
            Instant notBefore = jwt.getNotBefore();
            if (notBefore != null && Instant.now().plus(LOGOUT_NOT_BEFORE_CLOCK_SKEW).isBefore(notBefore)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "JWT is not active yet", null));
            }
            return OAuth2TokenValidatorResult.success();
        });
        this.logoutDecoder = expiredTokenDecoder;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, "access", properties.accessTokenTtl());
    }

    /** 로그인 세션의 Access Token을 세션 ID와 함께 발급한다. */
    public String createAccessToken(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("JWT session ID must not be blank");
        }
        return createToken(userId, "access", properties.accessTokenTtl(), sessionId);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, "refresh", properties.refreshTokenTtl());
    }

    /** 로그인 세션의 Refresh Token에 Access Token과 같은 세션 ID를 기록한다. */
    public String createRefreshToken(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("JWT session ID must not be blank");
        }
        return createToken(userId, "refresh", properties.refreshTokenTtl(), sessionId);
    }

    /** 새 로그인 세션을 식별할 예측하기 어려운 UUID를 생성한다. */
    public String createSessionId() {
        return UUID.randomUUID().toString();
    }

    public Long extractAccessUserId(String token) {
        return extractUserId(token, "access");
    }

    public Long extractRefreshUserId(String token) {
        return extractUserId(token, "refresh");
    }

    /** 서명을 검증한 Refresh Token에서 세션 ID를 읽고, 이전 토큰이면 빈 값을 반환한다. */
    public Optional<String> extractRefreshSessionId(String token) {
        Jwt jwt = decoder.decode(token);
        if (!"refresh".equals(jwt.getClaimAsString("tokenType"))) {
            throw new JwtException("JWT token type is invalid");
        }

        Object rawSessionId = jwt.getClaims().get(SESSION_ID_CLAIM);
        if (rawSessionId == null) {
            return Optional.empty();
        }
        if (!(rawSessionId instanceof String sessionId) || sessionId.isBlank()) {
            throw new JwtException("JWT session ID is invalid");
        }
        return Optional.of(sessionId);
    }

    /** 만료됐어도 서명 유효한 Refresh Token의 세션을 로그아웃 차단용으로 읽는다. */
    public RefreshTokenDetails extractRefreshTokenDetailsForLogout(String token) {
        Jwt jwt = logoutDecoder.decode(token);
        if (!"refresh".equals(jwt.getClaimAsString("tokenType"))) {
            throw new JwtException("JWT token type is invalid");
        }
        if (jwt.getExpiresAt() == null) {
            throw new JwtException("JWT expiration is missing");
        }

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new JwtException("JWT subject is invalid", exception);
        }

        Object rawSessionId = jwt.getClaims().get(SESSION_ID_CLAIM);
        if (rawSessionId != null && (!(rawSessionId instanceof String sessionId) || sessionId.isBlank())) {
            throw new JwtException("JWT session ID is invalid");
        }
        return new RefreshTokenDetails(userId, (String) rawSessionId);
    }

    /** 서명을 검증한 Access Token에서 사용자, 만료 시각, 선택적 세션 ID를 읽는다. */
    public AccessTokenDetails extractAccessTokenDetails(String token) {
        Jwt jwt = decoder.decode(token);
        if (!"access".equals(jwt.getClaimAsString("tokenType"))) {
            throw new JwtException("JWT token type is invalid");
        }

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new JwtException("JWT subject is invalid", exception);
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            throw new JwtException("JWT expiration is missing");
        }
        String sessionId = jwt.getClaimAsString(SESSION_ID_CLAIM);
        if (jwt.getClaims().containsKey(SESSION_ID_CLAIM) && (sessionId == null || sessionId.isBlank())) {
            throw new JwtException("JWT session ID is invalid");
        }
        return new AccessTokenDetails(userId, expiresAt, sessionId);
    }

    private Long extractUserId(String token, String expectedTokenType) {
        Jwt jwt = decoder.decode(token);
        if (!expectedTokenType.equals(jwt.getClaimAsString("tokenType"))) {
            throw new JwtException("JWT token type is invalid");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new JwtException("JWT subject is invalid", exception);
        }
    }

    private String createToken(Long userId, String tokenType, java.time.Duration ttl) {
        return createToken(userId, tokenType, ttl, null);
    }

    /** 토큰의 기본 claim을 만들고 세션 ID가 있으면 함께 서명한다. */
    private String createToken(Long userId, String tokenType, java.time.Duration ttl, String sessionId) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim("tokenType", tokenType);
        if (sessionId != null) {
            claimsBuilder.claim(SESSION_ID_CLAIM, sessionId);
        }
        JwtClaimsSet claims = claimsBuilder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record AccessTokenDetails(Long userId, Instant expiresAt, String sessionId) {
        /** 세션 ID를 포함하지 않던 기존 호출을 레거시 토큰 정보로 표현한다. */
        public AccessTokenDetails(Long userId, Instant expiresAt) {
            this(userId, expiresAt, null);
        }
    }

    /** 로그아웃 시 Refresh Token에서 복구한 사용자와 선택적 세션 식별자다. */
    public record RefreshTokenDetails(Long userId, String sessionId) { }
}
