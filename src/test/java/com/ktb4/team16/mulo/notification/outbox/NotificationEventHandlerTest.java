package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.notification.event.NotificationEventPayloadValidator;
import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NotificationEventHandlerTest {

    private final NotificationEventHandler handler = new NotificationEventHandler(
            new NotificationEventPayloadValidator(new ObjectMapper()));

    /** 친구 요청 당시 발신자 닉네임을 보존한 단일 수신자 알림으로 매핑한다. */
    @Test
    void createsFriendRequestCopyFromNicknameSnapshot() throws Exception {
        String payload = """
                {"payloadVersion":1,"friendRequestId":301,"senderUserId":10,
                 "senderNickname":"요청자","recipientUserIds":[20]}
                """;

        var draft = handler.handle(event(NotificationEventType.FRIEND_REQUEST, 301L, payload)).getFirst();

        assertThat(draft.recipientUserId()).isEqualTo(20L);
        assertThat(draft.targetId()).isEqualTo(301L);
        assertThat(draft.title()).isEqualTo("친구 요청");
        assertThat(draft.content()).isEqualTo("'요청자'님이 친구 요청을 보냈어요.");
    }

    /** 역방향 요청 자동 성립은 같은 friendship을 참조하는 두 문안을 생성한다. */
    @Test
    void createsTwoFriendAcceptanceCopiesForMutualRequest() throws Exception {
        String payload = """
                {"payloadVersion":1,"friendshipId":501,"acceptanceType":"MUTUAL_REQUEST",
                 "recipientUserIds":[10,20],"participants":[
                   {"userId":10,"nickname":"사용자A"},
                   {"userId":20,"nickname":"사용자B"}]}
                """;

        var drafts = handler.handle(event(NotificationEventType.FRIEND_ACCEPTED, 501L, payload));

        assertThat(drafts).containsExactly(
                new NotificationDraft(10L,
                        com.ktb4.team16.mulo.notification.entity.NotificationType.FRIEND_ACCEPTED,
                        501L, "친구 수락", "‘사용자B’님과 친구가 되었어요."),
                new NotificationDraft(20L,
                        com.ktb4.team16.mulo.notification.entity.NotificationType.FRIEND_ACCEPTED,
                        501L, "친구 수락", "‘사용자A’님과 친구가 되었어요."));
    }

    /** Outbox ID·event type·aggregate·payload를 공통 테스트 snapshot으로 만든다. */
    private OutboxEvent event(NotificationEventType type, Long aggregateId, String payload) {
        return new OutboxEvent(UUID.fromString("00000000-0000-0000-0000-000000000501"),
                type, aggregateId, payload, 0, null);
    }
}
