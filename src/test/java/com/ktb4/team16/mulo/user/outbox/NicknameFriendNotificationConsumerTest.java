package com.ktb4.team16.mulo.user.outbox;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.notification.service.NicknameFriendNotificationService;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.TransientDataAccessResourceException;
import tools.jackson.databind.json.JsonMapper;

class NicknameFriendNotificationConsumerTest {
    private final NicknameFriendNotificationService service = mock();
    private final NotificationCreatedNotifier notifier = mock();
    private final NicknameFriendNotificationConsumer consumer =
            new NicknameFriendNotificationConsumer(JsonMapper.builder().build(), service, notifier);

    /** 서비스 트랜잭션이 끝난 뒤 새 수신자에 SSE 신호를 보내고 RabbitMQ를 ACK한다. */
    @Test
    void acknowledgesCommittedNotificationAndSignalsRecipients() throws Exception {
        var channel = mock(Channel.class);
        var message = validMessage();
        when(service.process(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(13L, 14L));

        consumer.receive(message, channel);

        verify(notifier).notificationCreated(Set.of(13L, 14L));
        verify(channel).basicAck(9L, false);
    }

    /** 형식이 잘못된 이벤트는 친구 서비스에 넘기지 않고 전용 DLQ로 보낸다. */
    @Test
    void rejectsMalformedEventToDeadLetterQueue() throws Exception {
        var channel = mock(Channel.class);

        consumer.receive(message("{}"), channel);

        verify(channel).basicReject(9L, false);
        verify(service, never()).process(org.mockito.ArgumentMatchers.any());
    }

    /** DB 데드락 같은 일시 오류는 롤백 후 제한 횟수 안에서 다시 처리한다. */
    @Test
    void retriesTransientDatabaseFailureBeforeAcknowledging() throws Exception {
        var channel = mock(Channel.class);
        when(service.process(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new TransientDataAccessResourceException("temporary deadlock"))
                .thenReturn(Set.of(13L));

        consumer.receive(validMessage(), channel);

        verify(service, org.mockito.Mockito.times(2)).process(org.mockito.ArgumentMatchers.any());
        verify(notifier).notificationCreated(Set.of(13L));
        verify(channel).basicAck(9L, false);
    }

    /** 되돌릴 수 없는 상태 불일치는 자동 재시도하지 않고 DLQ로 보낸다. */
    @Test
    void rejectsPermanentProcessingFailureToDeadLetterQueue() throws Exception {
        var channel = mock(Channel.class);
        doThrow(new IllegalStateException("invalid current version"))
                .when(service).process(org.mockito.ArgumentMatchers.any());

        consumer.receive(validMessage(), channel);

        verify(channel).basicReject(9L, false);
        verify(notifier, never()).notificationCreated(org.mockito.ArgumentMatchers.anySet());
    }

    /** 친구 알림 이벤트 계약을 담은 JSON RabbitMQ 메시지를 만든다. */
    private Message validMessage() {
        return message("{\"schemaVersion\":1,\"eventId\":\"3d614731-1e6f-4a4c-a22f-761b28741f2f\","
                + "\"eventType\":\"USER_NICKNAME_CHANGED\",\"userId\":17,"
                + "\"nickname\":\"새닉네임\",\"nicknameVersion\":8,"
                + "\"occurredAt\":\"2026-11-20T12:01:00+09:00\"}");
    }

    /** 지정 JSON을 확인 가능한 수동 ACK deliveryTag로 감싼다. */
    private Message message(String json) {
        var properties = new MessageProperties();
        properties.setDeliveryTag(9L);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
