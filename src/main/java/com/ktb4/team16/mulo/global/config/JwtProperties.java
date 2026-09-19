package com.ktb4.team16.mulo.global.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mulo.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET must be configured");
        }
        if (accessTokenTtl == null || refreshTokenTtl == null) {
            throw new IllegalArgumentException("JWT token lifetimes must be configured");
        }
        if (decodedSecret(secret).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(decodedSecret(secret), "HmacSHA256");
    }

    private static byte[] decodedSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be Base64 encoded", exception);
        }
    }
}
