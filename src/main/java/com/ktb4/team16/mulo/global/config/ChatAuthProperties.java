package com.ktb4.team16.mulo.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chat-auth")
public record ChatAuthProperties(String verifyApiKey) { }
