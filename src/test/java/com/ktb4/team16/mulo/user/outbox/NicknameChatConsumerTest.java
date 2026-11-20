package com.ktb4.team16.mulo.user.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

class NicknameChatConsumerTest {
    private final ChatRoomClient chatRoomClient = mock(ChatRoomClient.class);
    private final NicknameChatConsumer consumer = new NicknameChatConsumer(JsonMapper.builder().build(), chatRoomClient);

    /** 정상 적용 결과를 받으면 Chat 큐 원본 메시지를 ACK한다. */
    @Test
    void acknowledgesAppliedVersionedNickname() throws Exception {
        UUID eventId = UUID.randomUUID();
        var channel = mock(com.rabbitmq.client.Channel.class);
        var message = message("{\"schemaVersion\":1,\"eventId\":\"" + eventId
                + "\",\"eventType\":\"USER_NICKNAME_CHANGED\",\"userId\":17,"
                + "\"nickname\":\"새닉네임\",\"nicknameVersion\":8,"
                + "\"occurredAt\":\"2026-11-20T09:00:00+09:00\"}");

        consumer.receive(message, channel);

        verify(chatRoomClient).updateParticipantNicknameVersioned(17L, "새닉네임", 8L, eventId);
        verify(channel).basicAck(9L, false);
    }

    /** JSON 계약이 잘못된 입력은 재시도하지 않고 Chat 전용 DLQ로 보낸다. */
    @Test
    void rejectsMalformedMessageToDeadLetterQueue() throws Exception {
        var channel = mock(com.rabbitmq.client.Channel.class);

        consumer.receive(message("{}"), channel);

        verify(channel).basicReject(9L, false);
    }

    private Message message(String json) {
        var properties = new MessageProperties();
        properties.setDeliveryTag(9L);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
