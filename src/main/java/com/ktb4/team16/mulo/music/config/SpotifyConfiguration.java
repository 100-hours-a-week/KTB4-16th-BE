package com.ktb4.team16.mulo.music.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Spotify 설정 레코드를 Spring Boot의 생성자 바인딩 방식으로 등록한다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpotifyProperties.class)
public class SpotifyConfiguration {
}
