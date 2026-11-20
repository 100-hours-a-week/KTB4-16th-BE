package com.ktb4.team16.mulo.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class NicknamePublisherTest {
    private final RabbitTemplate template = org.mockito.Mockito.mock(RabbitTemplate.class);
    private final NicknamePublisher publisher = new NicknamePublisher(template);

    /** 영구 JSON 메시지의 ID와 RabbitMQ 대상 큐를 검증한다. */
    @Test
    void publishesPersistentPayloadUsingOutboxEventId() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq(""), eq(NicknameMqConfiguration.CHAT), any(Message.class),
                any(CorrelationData.class));

        var claim = new ChatNicknameOutboxRepository.Claim("event-8", "{\"nicknameVersion\":8}", "token", 1);

        assertThat(publisher.send(claim, NicknameMqConfiguration.CHAT))
                .isEqualTo(NicknamePublisher.Outcome.CONFIRMED);

        var message = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq(""), eq(NicknameMqConfiguration.CHAT), message.capture(),
                any(CorrelationData.class));
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("event-8");
        assertThat(message.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"nicknameVersion\":8}");
    }

    /** 반환된 메시지는 publisher ACK보다 우선해 미라우팅으로 판정한다. */
    @Test
    void returnedMessageIsNotConfirmed() {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new org.springframework.amqp.core.ReturnedMessage(
                    message, 312, "NO_ROUTE", "", NicknameMqConfiguration.CHAT));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq(""), eq(NicknameMqConfiguration.CHAT), any(Message.class),
                any(CorrelationData.class));

        var claim = new ChatNicknameOutboxRepository.Claim("event-8", "{}", "token", 1);

        assertThat(publisher.send(claim, NicknameMqConfiguration.CHAT))
                .isEqualTo(NicknamePublisher.Outcome.UNROUTABLE);
    }
}
