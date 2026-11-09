package com.ktb4.team16.mulo.dailysong.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DailySongRabbitMqTopologyConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DailySongRabbitMqConfiguration.class, DailySongRabbitMqTopologyConfiguration.class);

    @Test
    void disabledDoesNotRegisterTopologyOrAdmin() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ConnectionFactory.class);
            assertThat(context).doesNotHaveBean(RabbitAdmin.class);
            assertThat(context.getBeansOfType(DirectExchange.class)).isEmpty();
            assertThat(context.getBeansOfType(Queue.class)).isEmpty();
            assertThat(context.getBeansOfType(Binding.class)).isEmpty();
        });
    }

    @Test
    void enabledRegistersDurableTopologyWithDeadLetterRoute() {
        contextRunner.withPropertyValues(
                "mulo.rabbitmq.enabled=true",
                "spring.rabbitmq.host=127.0.0.1",
                "spring.rabbitmq.port=1",
                "spring.rabbitmq.username=test-user",
                "spring.rabbitmq.password=test-password",
                "spring.rabbitmq.virtual-host=test-vhost")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RabbitAdmin.class);
                    assertThat(context.getBeansOfType(DirectExchange.class)).hasSize(2);
                    assertThat(context.getBeansOfType(Queue.class)).hasSize(2);
                    assertThat(context.getBeansOfType(Binding.class)).hasSize(2);

                    DirectExchange workExchange = context.getBean("dailySongWorkExchange", DirectExchange.class);
                    Queue workQueue = context.getBean("dailySongWorkQueue", Queue.class);
                    Binding workBinding = context.getBean("dailySongWorkBinding", Binding.class);
                    DirectExchange deadLetterExchange =
                            context.getBean("dailySongDeadLetterExchange", DirectExchange.class);
                    Queue deadLetterQueue = context.getBean("dailySongDeadLetterQueue", Queue.class);
                    Binding deadLetterBinding = context.getBean("dailySongDeadLetterBinding", Binding.class);

                    assertThat(workExchange.getName()).isEqualTo(DailySongRabbitMqTopologyConfiguration.WORK_EXCHANGE_NAME);
                    assertThat(workExchange.isDurable()).isTrue();
                    assertThat(workExchange.isAutoDelete()).isFalse();
                    assertThat(workQueue.getName()).isEqualTo(DailySongRabbitMqTopologyConfiguration.WORK_QUEUE_NAME);
                    assertThat(workQueue.isDurable()).isTrue();
                    assertThat(workQueue.getArguments()).containsOnlyKeys("x-dead-letter-exchange", "x-dead-letter-routing-key")
                            .containsEntry("x-dead-letter-exchange",
                                    DailySongRabbitMqTopologyConfiguration.DEAD_LETTER_EXCHANGE_NAME)
                            .containsEntry("x-dead-letter-routing-key",
                                    DailySongRabbitMqTopologyConfiguration.DEAD_LETTER_ROUTING_KEY);
                    assertThat(workBinding.getExchange()).isEqualTo(workExchange.getName());
                    assertThat(workBinding.getDestination()).isEqualTo(workQueue.getName());
                    assertThat(workBinding.getRoutingKey()).isEqualTo(DailySongRabbitMqTopologyConfiguration.WORK_ROUTING_KEY);

                    assertThat(deadLetterExchange.getName())
                            .isEqualTo(DailySongRabbitMqTopologyConfiguration.DEAD_LETTER_EXCHANGE_NAME);
                    assertThat(deadLetterExchange.isDurable()).isTrue();
                    assertThat(deadLetterExchange.isAutoDelete()).isFalse();
                    assertThat(deadLetterQueue.getName())
                            .isEqualTo(DailySongRabbitMqTopologyConfiguration.DEAD_LETTER_QUEUE_NAME);
                    assertThat(deadLetterQueue.isDurable()).isTrue();
                    assertThat(deadLetterQueue.getArguments()).isEmpty();
                    assertThat(deadLetterBinding.getExchange()).isEqualTo(deadLetterExchange.getName());
                    assertThat(deadLetterBinding.getDestination()).isEqualTo(deadLetterQueue.getName());
                    assertThat(deadLetterBinding.getRoutingKey())
                            .isEqualTo(DailySongRabbitMqTopologyConfiguration.DEAD_LETTER_ROUTING_KEY);
                });
    }
}
