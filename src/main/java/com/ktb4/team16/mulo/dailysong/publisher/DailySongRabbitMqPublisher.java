package com.ktb4.team16.mulo.dailysong.publisher;

import com.ktb4.team16.mulo.dailysong.config.DailySongRabbitMqTopologyConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
public class DailySongRabbitMqPublisher {
    private static final Logger log = LoggerFactory.getLogger(DailySongRabbitMqPublisher.class);
    private static final long CONFIRM_TIMEOUT_SECONDS = 10;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public DailySongRabbitMqPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public PublishOutcome publish(long jobId, String eventId) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(eventId);
            Message message = new Message(objectMapper.writeValueAsBytes(new DailySongJobMessage(jobId)), properties);
            CorrelationData correlation = new CorrelationData(eventId);
            rabbitTemplate.send(DailySongRabbitMqTopologyConfiguration.WORK_EXCHANGE_NAME,
                    DailySongRabbitMqTopologyConfiguration.WORK_ROUTING_KEY, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                return PublishOutcome.UNROUTABLE;
            }
            return confirm.ack() ? PublishOutcome.CONFIRMED : PublishOutcome.NACKED;
        } catch (JacksonException exception) {
            log.error("오늘의 노래 작업 메시지를 직렬화하지 못했습니다: jobId={}", jobId, exception);
            return PublishOutcome.SERIALIZATION_FAILED;
        } catch (TimeoutException exception) {
            log.warn("오늘의 노래 RabbitMQ 발행 확인 시간이 초과됐습니다: jobId={}", jobId);
            return PublishOutcome.CONFIRM_TIMEOUT;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("오늘의 노래 RabbitMQ 발행 확인이 중단됐습니다: jobId={}", jobId);
            return PublishOutcome.INTERRUPTED;
        } catch (ExecutionException | RuntimeException exception) {
            log.warn("오늘의 노래 RabbitMQ 메시지 발행에 실패했습니다: jobId={}", jobId, exception);
            return PublishOutcome.SEND_FAILED;
        }
    }

    public enum PublishOutcome {
        CONFIRMED, UNROUTABLE, NACKED, CONFIRM_TIMEOUT, INTERRUPTED, SERIALIZATION_FAILED, SEND_FAILED
    }
}
