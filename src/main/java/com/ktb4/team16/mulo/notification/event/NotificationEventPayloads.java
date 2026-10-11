package com.ktb4.team16.mulo.notification.event;

import java.util.List;

/** 이벤트 발행 시 저장하고 처리 시 다시 검증하는 버전 1 payload 계약이다. */
public final class NotificationEventPayloads {

    /** payload의 공통 버전과 알림 수신자를 표현한다. */
    public interface Payload {
        int payloadVersion();

        List<Long> recipientUserIds();
    }

    /** AI 회고 완료 시 리포트 소유자에게 보낼 payload다. */
    public record MonthlyReportCreated(
            int payloadVersion,
            List<Long> recipientUserIds,
            Long monthlyReportId,
            int reportYear,
            int reportMonth
    ) implements Payload {
    }

    /** 채팅방 등록 완료 후 발행하는 매칭 결과 payload다. */
    public record MatchCompleted(
            int payloadVersion,
            Long chatRoomId,
            int matchParticipantCount,
            List<Long> recipientUserIds
    ) implements Payload {
    }

    /** 친구 요청 당시 발신자 닉네임을 보존하는 payload다. */
    public record FriendRequest(
            int payloadVersion,
            Long friendRequestId,
            Long senderUserId,
            String senderNickname,
            List<Long> recipientUserIds
    ) implements Payload {
    }

    /** 명시 수락과 상호 요청 자동 성립을 구분하는 payload다. */
    public record FriendAccepted(
            int payloadVersion,
            Long friendshipId,
            AcceptanceType acceptanceType,
            List<Long> recipientUserIds,
            List<Participant> participants
    ) implements Payload {
    }

    /** 친구 수락 사건이 한 명 또는 양쪽에게 통지되는 이유다. */
    public enum AcceptanceType {
        EXPLICIT,
        MUTUAL_REQUEST
    }

    /** 수락 당시 양쪽 사용자 ID와 닉네임을 스냅샷으로 보존한다. */
    public record Participant(Long userId, String nickname) {
    }

    /** payload DTO 컨테이너는 인스턴스로 만들지 않는다. */
    private NotificationEventPayloads() {
    }
}
