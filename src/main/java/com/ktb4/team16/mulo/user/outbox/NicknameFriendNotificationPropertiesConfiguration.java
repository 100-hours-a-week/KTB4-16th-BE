package com.ktb4.team16.mulo.user.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** MQ 설정과 무관하게 친구 알림의 활성화 속성을 application context에 등록한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NicknameFriendNotificationProperties.class)
class NicknameFriendNotificationPropertiesConfiguration {
}
