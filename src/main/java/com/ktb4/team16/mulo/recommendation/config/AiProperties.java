package com.ktb4.team16.mulo.recommendation.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(URI baseUrl, String internalToken, Duration connectTimeout,
        Duration readTimeout, boolean mockEnabled) {
    public AiProperties {
        if (baseUrl == null || !"http".equalsIgnoreCase(baseUrl.getScheme())
                && !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("AI base URL must be configured");
        }
        if (!mockEnabled && (internalToken == null || internalToken.isBlank())) {
            throw new IllegalArgumentException("AI internal token must be configured");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("AI timeouts must be positive");
        }
    }
}
