package com.ktb4.team16.mulo.dailysong.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
public class DailySongRabbitMqPublisherConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DailySongRabbitMqPublisherConfiguration.class);

    @Bean
    RabbitTemplate dailySongRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> log.warn(
                "오늘의 노래 RabbitMQ 메시지가 라우팅되지 않았습니다: exchange={}, routingKey={}",
                returned.getExchange(), returned.getRoutingKey()));
        return template;
    }

    @Bean
    TaskScheduler dailySongPublisherTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("daily-song-publisher-");
        return scheduler;
    }
}
