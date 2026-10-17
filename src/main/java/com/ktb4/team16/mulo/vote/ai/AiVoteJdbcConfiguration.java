package com.ktb4.team16.mulo.vote.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiDatabaseProperties.class)
public class AiVoteJdbcConfiguration { }
