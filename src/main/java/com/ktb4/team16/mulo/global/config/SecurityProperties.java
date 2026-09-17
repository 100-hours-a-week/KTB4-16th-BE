package com.ktb4.team16.mulo.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("mulo.security")
public record SecurityProperties(
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue("true") boolean cookieSecure
) { }
