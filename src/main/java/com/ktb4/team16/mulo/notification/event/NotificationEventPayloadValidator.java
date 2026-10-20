package com.ktb4.team16.mulo.notification.event;

import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.AcceptanceType;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.FriendAccepted;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.FriendRequest;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.MatchCompleted;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.MonthlyReportCreated;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.Participant;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads.Payload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** 원본 도메인에서 발행할 때와 Outbox를 처리할 때 같은 payload 규칙을 적용한다. */
@Component
public class NotificationEventPayloadValidator {

    /** 현재 검증기가 처리할 수 있는 알림 이벤트 payload 형식의 버전이다. */
    private static final int SUPPORTED_PAYLOAD_VERSION = 1;

    private final ObjectMapper objectMapper;

    /** 프로젝트 Jackson 설정을 사용해 payload JSON을 읽고 쓴다. */
    public NotificationEventPayloadValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 저장된 JSON을 이벤트 종류에 맞게 역직렬화한 뒤 비즈니스 필드를 검증한다. */
    public Payload parseAndValidate(NotificationEventType eventType, String payloadJson) {
        Payload payload;
        try {
            payload = objectMapper.readValue(payloadJson, payloadClass(eventType));
        } catch (JacksonException exception) {
            throw new InvalidNotificationEventPayloadException("INVALID_PAYLOAD", exception);
        }
        validate(eventType, payload);
        return payload;
    }

    /** 발행 직전에도 수신자와 유형별 필수 값이 유효한지 확인한다. */
    public void validate(NotificationEventType eventType, Payload payload) {
        if (eventType == null || payload == null
                || payload.payloadVersion() != SUPPORTED_PAYLOAD_VERSION) {
            throw new InvalidNotificationEventPayloadException("UNSUPPORTED_PAYLOAD_VERSION");
        }
        validateRecipients(payload.recipientUserIds());

        switch (eventType) {
            case MONTHLY_REPORT_CREATED -> validateMonthlyReport(payload);
            case MATCH_COMPLETED -> validateMatchCompleted(payload);
            case FRIEND_REQUEST -> validateFriendRequest(payload);
            case FRIEND_ACCEPTED -> validateFriendAccepted(payload);
        }
    }

    /** 이벤트 종류가 요구하는 DTO 클래스를 반환한다. */
    private Class<? extends Payload> payloadClass(NotificationEventType eventType) {
        return switch (eventType) {
            case MONTHLY_REPORT_CREATED -> MonthlyReportCreated.class;
            case MATCH_COMPLETED -> MatchCompleted.class;
            case FRIEND_REQUEST -> FriendRequest.class;
            case FRIEND_ACCEPTED -> FriendAccepted.class;
        };
    }

    /** 비어 있거나 중복·비양수 ID가 있는 수신자 배열을 거부한다. */
    private void validateRecipients(List<Long> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            throw new InvalidNotificationEventPayloadException("RECIPIENTS_REQUIRED");
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long recipientId : recipientIds) {
            if (recipientId == null || recipientId <= 0 || !uniqueIds.add(recipientId)) {
                throw new InvalidNotificationEventPayloadException("INVALID_RECIPIENTS");
            }
        }
    }

    /** 리포트 ID와 연·월 및 단일 소유자 수신자를 확인한다. */
    private void validateMonthlyReport(Payload payload) {
        if (!(payload instanceof MonthlyReportCreated report)
                || !isPositive(report.monthlyReportId())
                || report.reportYear() <= 0
                || report.reportMonth() < 1 || report.reportMonth() > 12
                || report.recipientUserIds().size() != 1) {
            throw new InvalidNotificationEventPayloadException("INVALID_MONTHLY_REPORT_PAYLOAD");
        }
    }

    /** 현재 1:1 정책에 따라 매칭 ID와 두 명의 고유 수신자를 확인한다. */
    private void validateMatchCompleted(Payload payload) {
        if (!(payload instanceof MatchCompleted match)
                || !isPositive(match.chatRoomId())
                || match.matchParticipantCount() != 2
                || match.matchParticipantCount() != match.recipientUserIds().size()) {
            throw new InvalidNotificationEventPayloadException("INVALID_MATCH_PAYLOAD");
        }
    }

    /** 친구 요청 ID, 발신자 스냅샷과 한 명의 수신자를 확인한다. */
    private void validateFriendRequest(Payload payload) {
        if (!(payload instanceof FriendRequest request)
                || !isPositive(request.friendRequestId())
                || !isPositive(request.senderUserId())
                || request.senderNickname() == null || request.senderNickname().isBlank()
                || request.recipientUserIds().size() != 1
                || request.recipientUserIds().contains(request.senderUserId())) {
            throw new InvalidNotificationEventPayloadException("INVALID_FRIEND_REQUEST_PAYLOAD");
        }
    }

    /** 명시 수락 1명과 상호 요청 2명 규칙 및 두 참여자 스냅샷을 확인한다. */
    private void validateFriendAccepted(Payload payload) {
        if (!(payload instanceof FriendAccepted accepted)
                || !isPositive(accepted.friendshipId())
                || accepted.acceptanceType() == null
                || accepted.participants() == null
                || accepted.participants().size() != 2) {
            throw new InvalidNotificationEventPayloadException("INVALID_FRIEND_ACCEPTED_PAYLOAD");
        }

        Set<Long> participantIds = new HashSet<>();
        for (Participant participant : accepted.participants()) {
            if (participant == null || !isPositive(participant.userId())
                    || participant.nickname() == null || participant.nickname().isBlank()
                    || !participantIds.add(participant.userId())) {
                throw new InvalidNotificationEventPayloadException("INVALID_PARTICIPANTS");
            }
        }

        Set<Long> recipientIds = Set.copyOf(accepted.recipientUserIds());
        int expectedRecipientCount = accepted.acceptanceType() == AcceptanceType.EXPLICIT ? 1 : 2;
        if (recipientIds.size() != expectedRecipientCount || !participantIds.containsAll(recipientIds)
                || (accepted.acceptanceType() == AcceptanceType.MUTUAL_REQUEST
                    && !recipientIds.equals(participantIds))) {
            throw new InvalidNotificationEventPayloadException("INVALID_ACCEPTANCE_RECIPIENTS");
        }
    }

    /** nullable 식별자가 양수인지 확인한다. */
    private boolean isPositive(Long id) {
        return id != null && id > 0;
    }
}
