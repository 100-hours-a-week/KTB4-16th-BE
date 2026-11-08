package com.ktb4.team16.mulo.dailysong.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DailySongRabbitMqConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DailySongRabbitMqConfiguration.class);

    @Test
    void disabledByDefaultDoesNotCreateConnectionFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("mulo.rabbitmq.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("spring.autoconfigure.exclude"))
                    .isEqualTo("org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration");
            assertThat(context).doesNotHaveBean(ConnectionFactory.class);
        });
    }

    @Test
    void enabledConnectionUsesConfiguredPropertiesWithoutConnecting() {
        contextRunner.withPropertyValues(
                "mulo.rabbitmq.enabled=true",
                "spring.rabbitmq.host=127.0.0.1",
                "spring.rabbitmq.port=1",
                "spring.rabbitmq.username=test-user",
                "spring.rabbitmq.password=test-password",
                "spring.rabbitmq.virtual-host=test-vhost")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CachingConnectionFactory connectionFactory =
                            context.getBean(CachingConnectionFactory.class);
                    assertThat(connectionFactory.getHost()).isEqualTo("127.0.0.1");
                    assertThat(connectionFactory.getPort()).isEqualTo(1);
                    assertThat(connectionFactory.getUsername()).isEqualTo("test-user");
                    assertThat(connectionFactory.getRabbitConnectionFactory().getPassword())
                            .isEqualTo("test-password");
                    assertThat(connectionFactory.getVirtualHost()).isEqualTo("test-vhost");
                });
    }

    @Test
    void enabledConnectionRejectsMissingConfiguration() {
        contextRunner.withPropertyValues("mulo.rabbitmq.enabled=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause().hasMessage("RabbitMQ 연결 정보가 올바르게 설정되지 않았습니다."));
    }
}
