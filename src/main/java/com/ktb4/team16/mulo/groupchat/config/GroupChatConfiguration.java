package com.ktb4.team16.mulo.groupchat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GroupChatProperties.class)
public class GroupChatConfiguration {
}
