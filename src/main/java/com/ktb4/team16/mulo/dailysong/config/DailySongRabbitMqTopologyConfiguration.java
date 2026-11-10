package com.ktb4.team16.mulo.dailysong.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
public class DailySongRabbitMqTopologyConfiguration {
    public static final String WORK_EXCHANGE_NAME = "mulo.daily-song.work.exchange";
    public static final String WORK_QUEUE_NAME = "mulo.daily-song.work.queue";
    public static final String WORK_ROUTING_KEY = "mulo.daily-song.work";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "mulo.daily-song.dead-letter.exchange";
    public static final String DEAD_LETTER_QUEUE_NAME = "mulo.daily-song.dead-letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "mulo.daily-song.dead-letter";

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    DirectExchange dailySongWorkExchange() {
        return new DirectExchange(WORK_EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue dailySongWorkQueue() {
        return QueueBuilder.durable(WORK_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding dailySongWorkBinding(@Qualifier("dailySongWorkQueue") Queue workQueue,
            @Qualifier("dailySongWorkExchange") DirectExchange workExchange) {
        return BindingBuilder.bind(workQueue).to(workExchange).with(WORK_ROUTING_KEY);
    }

    @Bean
    DirectExchange dailySongDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE_NAME, true, false);
    }

    @Bean
    Queue dailySongDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    Binding dailySongDeadLetterBinding(@Qualifier("dailySongDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("dailySongDeadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }
}
