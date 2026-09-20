package com.ktb4.team16.mulo.global.security.jwt;

import com.ktb4.team16.mulo.global.config.JwtProperties;
import java.time.Instant;
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
    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.encoder = NimbusJwtEncoder.withSecretKey(properties.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
        this.decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, "access", properties.accessTokenTtl());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, "refresh", properties.refreshTokenTtl());
    }

    public Long extractAccessUserId(String token) {
        return extractUserId(token, "access");
    }

    public Long extractRefreshUserId(String token) {
        return extractUserId(token, "refresh");
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
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .claim("tokenType", tokenType)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
