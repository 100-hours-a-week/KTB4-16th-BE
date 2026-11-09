package com.ktb4.team16.mulo.dailysong.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.dailysong.publisher.DailySongOutboxDispatcher;
import com.ktb4.team16.mulo.dailysong.publisher.DailySongRabbitMqPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

class DailySongRabbitMqPublisherConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DailySongRabbitMqConfiguration.class,
                    DailySongRabbitMqPublisherConfiguration.class);

    @Test
    void disabledDoesNotRegisterPublisherComponents() {
        contextRunner.withUserConfiguration(DailySongRabbitMqPublisher.class, DailySongOutboxDispatcher.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RabbitTemplate.class);
                    assertThat(context).doesNotHaveBean(DailySongRabbitMqPublisher.class);
                    assertThat(context).doesNotHaveBean(DailySongOutboxDispatcher.class);
                    assertThat(context.getBeansOfType(TaskScheduler.class)).isEmpty();
                });
    }

    @Test
    void enabledRegistersTemplateAndDedicatedSchedulerWithoutConnecting() {
        contextRunner.withPropertyValues(
                "mulo.rabbitmq.enabled=true",
                "spring.rabbitmq.host=127.0.0.1",
                "spring.rabbitmq.port=1",
                "spring.rabbitmq.username=test-user",
                "spring.rabbitmq.password=test-password",
                "spring.rabbitmq.virtual-host=test-vhost")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RabbitTemplate.class);
                    assertThat(context.getBeansOfType(TaskScheduler.class)).hasSize(1);
                });
    }
}
