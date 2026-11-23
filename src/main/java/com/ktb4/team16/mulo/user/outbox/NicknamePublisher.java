package com.ktb4.team16.mulo.user.outbox;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RequiredArgsConstructor
class NicknamePublisher {
    private final RabbitTemplate template;

    enum Outcome { CONFIRMED, UNROUTABLE, NACKED, TIMEOUT, INTERRUPTED, SEND_FAILED }

    /** 보관된 JSON과 원본 이벤트 ID를 보내고 해당 전송의 반환 및 confirm을 확인한다. */
    Outcome send(ChatNicknameOutboxRepository.Claim claim, String queue) {
        var message = MessageBuilder.withBody(claim.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId(claim.eventId()).build();
        var correlation = new CorrelationData();
        try {
            template.send("", queue, message, correlation);
            var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                return Outcome.UNROUTABLE;
            }
            return confirm.ack() ? Outcome.CONFIRMED : Outcome.NACKED;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Outcome.INTERRUPTED;
        } catch (TimeoutException timeout) {
            return Outcome.TIMEOUT;
        } catch (ExecutionException | AmqpException failure) {
            return Outcome.SEND_FAILED;
        }
    }
}
