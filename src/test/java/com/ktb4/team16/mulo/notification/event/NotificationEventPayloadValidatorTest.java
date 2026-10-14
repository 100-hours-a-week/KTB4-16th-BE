package com.ktb4.team16.mulo.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NotificationEventPayloadValidatorTest {

    private final NotificationEventPayloadValidator validator =
            new NotificationEventPayloadValidator(new ObjectMapper());

    /** FRIEND_ACCEPTED 명시 수락의 한 명 수신자와 상호 요청의 두 명 수신자를 검증한다. */
    @Test
    void acceptsBothFriendAcceptanceRecipientRules() {
        var explicit = new NotificationEventPayloads.FriendAccepted(
                1, 501L, NotificationEventPayloads.AcceptanceType.EXPLICIT, List.of(10L),
                List.of(new NotificationEventPayloads.Participant(10L, "사용자A"),
                        new NotificationEventPayloads.Participant(20L, "사용자B")));
        var mutual = new NotificationEventPayloads.FriendAccepted(
                1, 502L, NotificationEventPayloads.AcceptanceType.MUTUAL_REQUEST,
                List.of(10L, 20L),
                List.of(new NotificationEventPayloads.Participant(10L, "사용자A"),
                        new NotificationEventPayloads.Participant(20L, "사용자B")));

        validator.validate(NotificationEventType.FRIEND_ACCEPTED, explicit);
        validator.validate(NotificationEventType.FRIEND_ACCEPTED, mutual);

        assertThat(explicit.recipientUserIds()).containsExactly(10L);
        assertThat(mutual.recipientUserIds()).containsExactly(10L, 20L);
    }

    /** 중복·비양수 수신자와 이벤트 계약에 맞지 않는 수신자 수를 영구 오류로 거부한다. */
    @Test
    void rejectsDuplicateNonPositiveAndWrongCountRecipients() {
        var duplicateRecipients = new NotificationEventPayloads.FriendRequest(
                1, 301L, 10L, "요청자", List.of(20L, 20L));
        var nonPositiveRecipient = new NotificationEventPayloads.FriendRequest(
                1, 301L, 10L, "요청자", List.of(0L));
        var wrongAcceptanceCount = new NotificationEventPayloads.FriendAccepted(
                1, 501L, NotificationEventPayloads.AcceptanceType.EXPLICIT,
                List.of(10L, 20L),
                List.of(new NotificationEventPayloads.Participant(10L, "사용자A"),
                        new NotificationEventPayloads.Participant(20L, "사용자B")));

        assertThatThrownBy(() -> validator.validate(NotificationEventType.FRIEND_REQUEST,
                duplicateRecipients)).isInstanceOf(InvalidNotificationEventPayloadException.class);
        assertThatThrownBy(() -> validator.validate(NotificationEventType.FRIEND_REQUEST,
                nonPositiveRecipient)).isInstanceOf(InvalidNotificationEventPayloadException.class);
        assertThatThrownBy(() -> validator.validate(NotificationEventType.FRIEND_ACCEPTED,
                wrongAcceptanceCount)).isInstanceOf(InvalidNotificationEventPayloadException.class);
    }

    /** 현재 확정된 1:1 매칭 정책이 과거 4인 Payload를 거부하는지 검증한다. */
    @Test
    void rejectsLegacyFourParticipantMatchPayload() {
        var fourParticipantMatch = new NotificationEventPayloads.MatchCompleted(
                1, 502L, 4, List.of(10L, 20L, 30L, 40L));

        assertThatThrownBy(() -> validator.validate(
                NotificationEventType.MATCH_COMPLETED, fourParticipantMatch))
                .isInstanceOf(InvalidNotificationEventPayloadException.class);
    }

    /** 현재 확정된 1:1 Payload는 두 사용자 수와 중복 없는 수신자 수가 일치해야 한다. */
    @Test
    void acceptsTwoParticipantMatchPayload() {
        var twoParticipantMatch = new NotificationEventPayloads.MatchCompleted(
                1, 501L, 2, List.of(10L, 20L));

        validator.validate(NotificationEventType.MATCH_COMPLETED, twoParticipantMatch);
    }

    /** 월간 리포트 payload가 단일 수신자와 실제 연·월 범위를 지키는지 검증한다. */
    @Test
    void rejectsInvalidMonthlyReportPeriodAndRecipientCount() {
        var invalidMonth = new NotificationEventPayloads.MonthlyReportCreated(
                1, List.of(42L), 105L, 2026, 13);
        var multipleRecipients = new NotificationEventPayloads.MonthlyReportCreated(
                1, List.of(42L, 43L), 105L, 2026, 9);

        assertThatThrownBy(() -> validator.validate(NotificationEventType.MONTHLY_REPORT_CREATED,
                invalidMonth)).isInstanceOf(InvalidNotificationEventPayloadException.class);
        assertThatThrownBy(() -> validator.validate(NotificationEventType.MONTHLY_REPORT_CREATED,
                multipleRecipients)).isInstanceOf(InvalidNotificationEventPayloadException.class);
    }

    /** 저장된 JSON을 읽을 때도 버전과 필수 값 검증이 같은 방식으로 적용되는지 확인한다. */
    @Test
    void validatesSerializedPayloadAgainWhenHandlingOutbox() throws Exception {
        String validJson = """
                {"payloadVersion":1,"friendRequestId":301,"senderUserId":10,
                 "senderNickname":"요청자","recipientUserIds":[20]}
                """;
        String unsupportedVersion = """
                {"payloadVersion":2,"friendRequestId":301,"senderUserId":10,
                 "senderNickname":"요청자","recipientUserIds":[20]}
                """;

        NotificationEventPayloads.Payload parsed = validator.parseAndValidate(
                NotificationEventType.FRIEND_REQUEST, validJson);

        assertThat(parsed).isInstanceOf(NotificationEventPayloads.FriendRequest.class);
        assertThatThrownBy(() -> validator.parseAndValidate(
                NotificationEventType.FRIEND_REQUEST, unsupportedVersion))
                .isInstanceOf(InvalidNotificationEventPayloadException.class);
    }
}
