package com.ktb4.team16.mulo.notification.outbox;

import com.ktb4.team16.mulo.notification.event.NotificationEventPayloadValidator;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.FriendAccepted;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.FriendRequest;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.MatchCompleted;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.MonthlyReportCreated;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.Payload;
import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 원본 도메인 트랜잭션에 이벤트를 기록하고 처리용 ID를 반환한다. */
@Service
public class NotificationOutboxEventPublisher {

    private final NotificationOutboxRepository outbox;
    private final NotificationEventPayloadValidator validator;
    private final ObjectMapper objectMapper;

    /** 원본 저장소와 이벤트 검증·직렬화·처리 경로를 연결한다. */
    public NotificationOutboxEventPublisher(NotificationOutboxRepository outbox,
            NotificationEventPayloadValidator validator, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /** 유효한 이벤트를 같은 DB 트랜잭션에 저장하고 후속 처리를 위한 ID를 돌려준다. */
    public UUID publish(NotificationEventType eventType, Long aggregateId, Payload payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Outbox 이벤트는 원본 도메인 트랜잭션 안에서 발행해야 합니다.");
        }
        validator.validate(eventType, payload);
        if (!Objects.equals(aggregateId, aggregateId(eventType, payload))) {
            throw new IllegalArgumentException("Outbox aggregate ID가 payload와 일치하지 않습니다.");
        }
        return outbox.insertIfAbsent(eventType, aggregateId, serialize(payload));
    }

    /** 이벤트 종류에서 멱등성 키로 쓸 원본 resource ID를 추출한다. */
    private Long aggregateId(NotificationEventType eventType, Payload payload) {
        return switch (eventType) {
            case MONTHLY_REPORT_CREATED -> ((MonthlyReportCreated) payload).monthlyReportId();
            case MATCH_COMPLETED -> ((MatchCompleted) payload).chatRoomId();
            case FRIEND_REQUEST -> ((FriendRequest) payload).friendRequestId();
            case FRIEND_ACCEPTED -> ((FriendAccepted) payload).friendshipId();
        };
    }

    /** 검증된 payload를 Outbox JSON으로 직렬화한다. */
    private String serialize(Payload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("알림 payload를 JSON으로 저장할 수 없습니다.", exception);
        }
    }
}
