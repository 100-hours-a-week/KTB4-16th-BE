package com.ktb4.team16.mulo.dailysong.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.dailysong.config.DailySongRabbitMqTopologyConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class DailySongRabbitMqPublisherTest {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final DailySongRabbitMqPublisher publisher = new DailySongRabbitMqPublisher(
            template, JsonMapper.builder().build());

    @Test
    void confirmedPublishUsesPersistentJsonAndWorkRoute() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(42, "event-42"))
                .isEqualTo(DailySongRabbitMqPublisher.PublishOutcome.CONFIRMED);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).send(eq(DailySongRabbitMqTopologyConfiguration.WORK_EXCHANGE_NAME),
                eq(DailySongRabbitMqTopologyConfiguration.WORK_ROUTING_KEY), message.capture(), correlation.capture());
        assertThat(message.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("event-42");
        assertThat(new String(message.getValue().getBody())).isEqualTo("{\"jobId\":42}");
        assertThat(correlation.getValue().getId()).isEqualTo("event-42");
    }

    @Test
    void nackIsNotPublished() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "거절"));
            return null;
        }).when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(42, "event-42"))
                .isEqualTo(DailySongRabbitMqPublisher.PublishOutcome.NACKED);
    }

    @Test
    void returnedMessageIsNotPublishedEvenAfterAck() {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", "exchange", "route"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(42, "event-42"))
                .isEqualTo(DailySongRabbitMqPublisher.PublishOutcome.UNROUTABLE);
    }

    @Test
    void sendFailureIsRetriable() {
        doThrow(new AmqpException("연결 실패") { })
                .when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(42, "event-42"))
                .isEqualTo(DailySongRabbitMqPublisher.PublishOutcome.SEND_FAILED);
    }
}
