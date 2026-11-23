package com.ktb4.team16.mulo.user.outbox;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** RabbitMQ에서 닉네임 이벤트를 받아 기존 내부 API를 통해 Chat 상태에 적용한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"mulo.rabbitmq.enabled", "mulo.nickname.dispatch.enabled"}, havingValue = "true")
class NicknameChatConsumer {
    private static final Logger log = LoggerFactory.getLogger(NicknameChatConsumer.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100;
    private static final int MAX_NICKNAME_LENGTH = 10;

    private final ObjectMapper objectMapper;
    private final ChatRoomClient chatRoomClient;

    /** 버전 적용 성공을 ACK하고 잘못된 이벤트·반복 실패를 Chat 전용 DLQ로 보낸다. */
    @RabbitListener(queues = NicknameMqConfiguration.CHAT, containerFactory = "nicknameListenerContainerFactory")
    void receive(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ChatNicknameChangedMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), ChatNicknameChangedMessage.class);
            if (!isValid(event)) {
                channel.basicReject(deliveryTag, false);
                return;
            }
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("잘못된 닉네임 이벤트를 Chat DLQ로 보냅니다.");
            channel.basicReject(deliveryTag, false);
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                chatRoomClient.updateParticipantNicknameVersioned(event.userId(), event.nickname(),
                        event.nicknameVersion(), event.eventId());
                channel.basicAck(deliveryTag, false);
                return;
            } catch (ChatServerClientException failure) {
                if (!failure.failureType().isRetryable() || attempt == MAX_ATTEMPTS) {
                    log.warn("Chat 닉네임 이벤트 처리를 DLQ로 보냅니다: userId={}, version={}, attempt={}, failureType={}",
                            event.userId(), event.nicknameVersion(), attempt, failure.failureType());
                    channel.basicReject(deliveryTag, false);
                    return;
                }
                if (!waitBeforeRetry()) {
                    channel.basicReject(deliveryTag, false);
                    return;
                }
            } catch (RuntimeException failure) {
                log.error("Chat 닉네임 이벤트의 결과가 불확실해 DLQ로 보냅니다: eventId={}", event.eventId(), failure);
                channel.basicReject(deliveryTag, false);
                return;
            }
        }
    }

    /** 이벤트 계약의 필수 값과 스키마 버전을 확인한다. */
    private boolean isValid(ChatNicknameChangedMessage event) {
        return event != null && event.schemaVersion() == ChatNicknameChangedMessage.SCHEMA_VERSION && ChatNicknameChangedMessage.EVENT_TYPE.equals(event.eventType())
                && event.eventId() != null && event.userId() != null && event.userId() > 0
                && event.nickname() != null && !event.nickname().isBlank() && event.nickname().length() <= MAX_NICKNAME_LENGTH
                && event.nicknameVersion() > 0;
    }

    /** 일시적 네트워크 오류 뒤 짧은 간격을 두고 재시도한다. */
    private boolean waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
