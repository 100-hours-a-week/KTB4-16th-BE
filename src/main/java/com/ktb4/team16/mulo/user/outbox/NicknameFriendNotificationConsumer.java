package com.ktb4.team16.mulo.user.outbox;

import com.ktb4.team16.mulo.notification.service.NicknameFriendNotificationService;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 닉네임 이벤트를 친구별 알림으로 저장한 뒤 기존 SSE 신호를 전달한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"mulo.rabbitmq.enabled", "mulo.nickname.dispatch.enabled",
        "mulo.nickname.friend-notifications.enabled"}, havingValue = "true")
class NicknameFriendNotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(NicknameFriendNotificationConsumer.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100;
    private static final int MAX_NICKNAME_LENGTH = 10;

    private final ObjectMapper objectMapper;
    private final NicknameFriendNotificationService service;
    private final NotificationCreatedNotifier notificationCreatedNotifier;

    /** DB 커밋 성공을 ACK하고 입력 오류·재시도 소진은 친구 알림 전용 DLQ로 보낸다. */
    @RabbitListener(queues = NicknameMqConfiguration.FRIEND,
            containerFactory = "nicknameListenerContainerFactory")
    void receive(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ChatNicknameChangedMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), ChatNicknameChangedMessage.class);
            if (!isValid(event)) {
                reject(channel, deliveryTag, "invalid_event");
                return;
            }
        } catch (JacksonException | IllegalArgumentException invalid) {
            reject(channel, deliveryTag, "invalid_json");
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Set<Long> recipientIds = service.process(event);
                notifyAfterCommit(recipientIds);
                channel.basicAck(deliveryTag, false);
                return;
            } catch (DataIntegrityViolationException permanent) {
                log.error("친구 닉네임 알림 데이터 제약 오류를 DLQ로 보냅니다: eventId={}", event.eventId(), permanent);
                channel.basicReject(deliveryTag, false);
                return;
            } catch (DataAccessException transientOrStorageFailure) {
                if (attempt == MAX_ATTEMPTS || !waitBeforeRetry(attempt)) {
                    log.warn("친구 닉네임 알림 DB 재시도가 소진되어 DLQ로 보냅니다: eventId={}, attempts={}",
                            event.eventId(), attempt);
                    channel.basicReject(deliveryTag, false);
                    return;
                }
            } catch (RuntimeException permanent) {
                log.error("친구 닉네임 알림 처리 오류를 DLQ로 보냅니다: eventId={}", event.eventId(), permanent);
                channel.basicReject(deliveryTag, false);
                return;
            }
        }
    }

    /** DB 트랜잭션이 종료된 뒤 신규 알림 수신자만 목록 동기화 신호를 받는다. */
    private void notifyAfterCommit(Set<Long> recipientIds) {
        if (recipientIds.isEmpty()) {
            return;
        }
        try {
            notificationCreatedNotifier.notificationCreated(recipientIds);
        } catch (RuntimeException failure) {
            // 알림 행은 이미 커밋됐으므로 SSE 신호 실패가 RabbitMQ 이벤트를 다시 처리하게 만들지 않는다.
            log.warn("친구 닉네임 알림 SSE 신호를 제출하지 못했습니다: failureType={}",
                    failure.getClass().getSimpleName());
        }
    }

    /** 메시지 종류, 버전, 수신자 식별자와 발생 시각을 확인한다. */
    private boolean isValid(ChatNicknameChangedMessage event) {
        if (event == null || event.schemaVersion() != ChatNicknameChangedMessage.SCHEMA_VERSION || !ChatNicknameChangedMessage.EVENT_TYPE.equals(event.eventType())
                || event.eventId() == null || event.userId() == null || event.userId() <= 0
                || event.nickname() == null || event.nickname().isBlank() || event.nickname().length() > MAX_NICKNAME_LENGTH
                || event.nicknameVersion() <= 0 || event.occurredAt() == null) {
            return false;
        }
        try {
            OffsetDateTime.parse(event.occurredAt());
            return true;
        } catch (DateTimeParseException invalidTimestamp) {
            return false;
        }
    }

    /** 짧은 선형 지연 뒤 DB 처리를 재시도하고 종료 시 인터럽트 상태를 보존한다. */
    private boolean waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 잘못된 메시지를 자동 재전달하지 않고 큐에 설정된 친구 DLQ로 보낸다. */
    private void reject(Channel channel, long deliveryTag, String reason) throws IOException {
        log.warn("친구 닉네임 이벤트를 DLQ로 보냅니다: reason={}", reason);
        channel.basicReject(deliveryTag, false);
    }
}
