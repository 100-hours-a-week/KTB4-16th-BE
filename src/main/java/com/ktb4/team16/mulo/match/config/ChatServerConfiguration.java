package com.ktb4.team16.mulo.match.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatServerProperties.class)
public class ChatServerConfiguration {
}
