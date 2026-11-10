package com.ktb4.team16.mulo.dailysong.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DailySongRabbitMqConsumerConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withUserConfiguration(DailySongRabbitMqConsumerConfiguration.class);

    @Test
    void disabledDoesNotRegisterListenerFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("dailySongListenerContainerFactory");
        });
    }

    @Test
    void enabledRegistersListenerFactoryWithoutConnectingBroker() {
        contextRunner.withPropertyValues("mulo.rabbitmq.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("dailySongListenerContainerFactory"))
                            .isInstanceOf(SimpleRabbitListenerContainerFactory.class);
                });
    }
}
