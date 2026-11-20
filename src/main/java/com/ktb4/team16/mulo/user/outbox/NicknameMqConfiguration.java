package com.ktb4.team16.mulo.user.outbox;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mulo.rabbitmq.enabled", "mulo.nickname.dispatch.enabled"}, havingValue = "true")
@EnableRabbit
class NicknameMqConfiguration {
    static final String CHAT = "mulo.nickname.chat";
    static final String FRIEND = "mulo.nickname.friend";

    /** 기본 exchange의 큐 이름 라우팅으로 소비자별 작업 큐와 DLQ를 구성한다. */
    @Bean
    Declarables nicknameQueues() {
        return new Declarables(workQueue(CHAT), QueueBuilder.durable(CHAT + ".dead").build(),
                workQueue(FRIEND), QueueBuilder.durable(FRIEND + ".dead").build());
    }

    /** 영구 반려된 메시지를 해당 소비자의 전용 DLQ로 보낸다. */
    private Queue workQueue(String name) {
        return QueueBuilder.durable(name).deadLetterExchange("").deadLetterRoutingKey(name + ".dead").build();
    }

    /** 소비 처리의 DB 커밋 후 수동 ACK와 단일 순차 전달을 사용한다. */
    @Bean
    SimpleRabbitListenerContainerFactory nicknameListenerContainerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }

    /** 닉네임 발행에만 사용할 mandatory 템플릿을 구성한다. */
    @Bean
    NicknamePublisher nicknamePublisher(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return new NicknamePublisher(template);
    }

    /** 브로커 확인 대기가 다른 작업 스케줄러를 점유하지 않게 한다. */
    @Bean
    ThreadPoolTaskScheduler nicknameDispatchScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("nickname-dispatch-");
        return scheduler;
    }

    /** 두 목적지 발행을 담당하는 작업을 기능 활성화 시 등록한다. */
    @Bean
    NicknameOutboxDispatcher nicknameDispatcher(ChatNicknameOutboxRepository repository, NicknamePublisher publisher) {
        return new NicknameOutboxDispatcher(repository, publisher);
    }
}
