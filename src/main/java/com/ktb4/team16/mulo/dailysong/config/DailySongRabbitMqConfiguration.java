package com.ktb4.team16.mulo.dailysong.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RabbitProperties.class)
public class DailySongRabbitMqConfiguration {

    @Bean
    ConnectionFactory rabbitConnectionFactory(RabbitProperties properties) {
        if (properties.getHost() == null || properties.getHost().isBlank()
                || properties.getPort() < 1 || properties.getPort() > 65535
                || properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()
                || properties.getVirtualHost() == null || properties.getVirtualHost().isBlank()) {
            throw new IllegalStateException("RabbitMQ 연결 정보가 올바르게 설정되지 않았습니다.");
        }

        CachingConnectionFactory connectionFactory =
                new CachingConnectionFactory(properties.getHost(), properties.getPort());
        connectionFactory.setUsername(properties.getUsername());
        connectionFactory.setPassword(properties.getPassword());
        connectionFactory.setVirtualHost(properties.getVirtualHost());
        connectionFactory.setPublisherConfirmType(ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        return connectionFactory;
    }
}
