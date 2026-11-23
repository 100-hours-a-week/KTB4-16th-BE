package com.ktb4.team16.mulo.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ChatNicknameSyncAfterCommitListenerConditionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ListenerConfiguration.class)
            .withBean(UserProfileService.class, () -> mock(UserProfileService.class))
            .withBean(ChatRoomClient.class, () -> mock(ChatRoomClient.class));

    /** MQ를 요청했지만 RabbitMQ가 꺼져 있으면 기존 HTTP 동기화 경로를 계속 등록한다. */
    @Test
    void keepsHttpListenerWhenNicknameMqEnabledButRabbitMqDisabled() {
        contextRunner.withPropertyValues(
                "mulo.nickname.dispatch.enabled=true", "mulo.rabbitmq.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(ChatNicknameSyncAfterCommitListener.class))
                        .hasSize(1));
    }

    /** 두 MQ 기능이 모두 켜진 경우에만 기존 HTTP 리스너를 비활성화한다. */
    @Test
    void disablesHttpListenerWhenBothNicknameMqAndRabbitMqAreEnabled() {
        contextRunner.withPropertyValues(
                "mulo.nickname.dispatch.enabled=true", "mulo.rabbitmq.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(ChatNicknameSyncAfterCommitListener.class))
                        .isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ChatNicknameSyncAfterCommitListener.class)
    static class ListenerConfiguration {
    }
}
