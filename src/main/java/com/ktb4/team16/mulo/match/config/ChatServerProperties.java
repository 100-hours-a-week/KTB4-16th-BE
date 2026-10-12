package com.ktb4.team16.mulo.match.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat-server")
public record ChatServerProperties(
        URI baseUrl,
        String internalApiKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    public ChatServerProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !"http".equalsIgnoreCase(baseUrl.getScheme())
                && !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Chat Server base URL must be configured");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Chat Server timeouts must be positive");
        }
    }

    public boolean hasInternalApiKey() {
        return internalApiKey != null && !internalApiKey.isBlank();
    }
}
