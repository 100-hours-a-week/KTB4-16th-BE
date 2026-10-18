package com.ktb4.team16.mulo.vote.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mulo.ai-db")
public record AiDatabaseProperties(boolean enabled, String url, String username, String password) { }
