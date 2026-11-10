package com.ktb4.team16.mulo.dailysong.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
@EnableRabbit
public class DailySongRabbitMqConsumerConfiguration {
    @Bean
    SimpleRabbitListenerContainerFactory dailySongListenerContainerFactory(ConnectionFactory connectionFactory,
            @Value("${MULO_DAILY_SONG_CONSUMER_CONCURRENCY:1}") int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("오늘의 노래 Consumer 동시성은 1 이상이어야 합니다.");
        }
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        return factory;
    }

    @Bean
    TaskScheduler dailySongConsumerRecoveryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("daily-song-consumer-recovery-");
        return scheduler;
    }
}
