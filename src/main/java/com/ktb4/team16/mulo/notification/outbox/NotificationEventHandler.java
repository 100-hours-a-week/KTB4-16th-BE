package com.ktb4.team16.mulo.notification.outbox;

import com.ktb4.team16.mulo.notification.entity.NotificationType;
import com.ktb4.team16.mulo.notification.event.InvalidNotificationEventPayloadException;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloadValidator;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.FriendAccepted;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.FriendRequest;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.MonthlyReportCreated;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.Payload;
import org.springframework.stereotype.Component;

/** 검증된 이벤트 스냅샷을 저장할 사용자별 알림 문안으로 변환한다. */
@Component
public class NotificationEventHandler {

    private final NotificationEventPayloadValidator validator;

    /** 처리 시 payload 재검증에 사용할 공통 validator를 연결한다. */
    public NotificationEventHandler(NotificationEventPayloadValidator validator) {
        this.validator = validator;
    }

    /** 이벤트 집계 ID와 payload를 확인하고 수신자별 알림 초안을 만든다. */
    public java.util.List<NotificationDraft> handle(OutboxEvent event) {
        Payload payload = validator.parseAndValidate(event.eventType(), event.payload());
        return switch (event.eventType()) {
            case MONTHLY_REPORT_CREATED -> monthlyReport(event, (MonthlyReportCreated) payload);
            case FRIEND_REQUEST -> friendRequest(event, (FriendRequest) payload);
            case FRIEND_ACCEPTED -> friendAccepted(event, (FriendAccepted) payload);
            case MATCH_COMPLETED -> throw new NotificationEventPermanentException(
                    "MATCH_COMPLETION_PROTOCOL_UNAVAILABLE");
        };
    }

    /** AI 완료 당시 연·월을 문안에 포함한 리포트 알림을 만든다. */
    private java.util.List<NotificationDraft> monthlyReport(
            OutboxEvent event, MonthlyReportCreated payload) {
        verifyAggregate(event, payload.monthlyReportId());
        String content = "%d년 %d월 리포트가 도착했어요."
                .formatted(payload.reportYear(), payload.reportMonth());
        return java.util.List.of(new NotificationDraft(payload.recipientUserIds().getFirst(),
                NotificationType.MONTHLY_REPORT_CREATED, payload.monthlyReportId(),
                "월간 리포트", content));
    }

    /** 요청 당시 발신자 닉네임을 사용해 한 명의 친구 요청 알림을 만든다. */
    private java.util.List<NotificationDraft> friendRequest(OutboxEvent event, FriendRequest payload) {
        verifyAggregate(event, payload.friendRequestId());
        String content = "'%s'님이 친구 요청을 보냈어요.".formatted(payload.senderNickname());
        return java.util.List.of(new NotificationDraft(payload.recipientUserIds().getFirst(),
                NotificationType.FRIEND_REQUEST, payload.friendRequestId(), "친구 요청", content));
    }

    /** 수락 당시 상대 닉네임을 골라 명시 수락 또는 양쪽 자동 성립 알림을 만든다. */
    private java.util.List<NotificationDraft> friendAccepted(OutboxEvent event, FriendAccepted payload) {
        verifyAggregate(event, payload.friendshipId());
        return payload.recipientUserIds().stream().map(recipientId -> {
            String otherNickname = payload.participants().stream()
                    .filter(participant -> !participant.userId().equals(recipientId))
                    .map(com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.Participant::nickname)
                    .findFirst()
                    .orElseThrow(() -> new InvalidNotificationEventPayloadException(
                            "ACCEPTED_RECIPIENT_NOT_PARTICIPANT"));
            String content = "‘%s’님과 친구가 되었어요.".formatted(otherNickname);
            return new NotificationDraft(recipientId, NotificationType.FRIEND_ACCEPTED,
                    payload.friendshipId(), "친구 수락", content);
        }).toList();
    }

    /** Outbox 중복 방지 키와 payload 원본 리소스가 같은 사건인지 확인한다. */
    private void verifyAggregate(OutboxEvent event, Long payloadAggregateId) {
        if (!event.aggregateId().equals(payloadAggregateId)) {
            throw new InvalidNotificationEventPayloadException("AGGREGATE_ID_MISMATCH");
        }
    }
}
