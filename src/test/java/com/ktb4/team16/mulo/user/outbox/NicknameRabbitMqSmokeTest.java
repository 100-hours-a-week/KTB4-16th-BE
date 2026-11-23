package com.ktb4.team16.mulo.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 명시적으로 켠 로컬 RabbitMQ에서 닉네임 Outbox와 독립 소비자 두 경로를 검증한다. */
@SpringBootTest(properties = {
        "mulo.rabbitmq.enabled=true",
        "mulo.nickname.dispatch.enabled=true",
        "mulo.nickname.friend-notifications.enabled=true",
        "mulo.nickname.friend-notifications.enabled-at=2026-11-20T00:00:00+09:00"
})
@EnabledIfEnvironmentVariable(named = "MULO_NICKNAME_MQ_IT_ENABLED", matches = "true")
class NicknameRabbitMqSmokeTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserProfileService userProfileService;
    @MockitoBean private ChatRoomClient chatRoomClient;
    @MockitoBean private NotificationCreatedNotifier notifier;

    private final List<Long> userIds = new ArrayList<>();
    private UUID eventId;

    /** 스모크 테스트가 만든 알림·이력·Outbox·친구와 사용자를 제거한다. */
    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbc.update("DELETE FROM notifications WHERE source_event_id = ?", eventId.toString());
            jdbc.update("DELETE FROM nickname_notification_events WHERE event_id = ?", eventId.toString());
        }
        if (userIds.size() == 2) {
            Long actorId = userIds.getFirst();
            Long friendId = userIds.get(1);
            jdbc.update("DELETE FROM notifications WHERE user_id IN (?, ?)", actorId, friendId);
            jdbc.update("DELETE FROM friendships WHERE user_low_id IN (?, ?) OR user_high_id IN (?, ?)",
                    actorId, friendId, actorId, friendId);
            jdbc.update("DELETE FROM chat_nickname_outbox WHERE user_id = ?", actorId);
            jdbc.update("DELETE FROM users WHERE user_id IN (?, ?)", actorId, friendId);
        }
    }

    /** 실제 브로커 confirm 뒤 Chat·친구 소비자가 각각 처리하고 친구 알림과 SSE 신호를 남긴다. */
    @Test
    void publishesToBothQueuesAndStoresFriendNotification() throws Exception {
        long actorId = createUser("actor");
        long friendId = createUser("friend");
        long lowId = Math.min(actorId, friendId);
        long highId = Math.max(actorId, friendId);
        jdbc.update("INSERT INTO friendships (user_low_id, user_high_id) VALUES (?, ?)", lowId, highId);

        String newNickname = "mq" + UUID.randomUUID().toString().substring(0, 7);
        userProfileService.updateNickname(actorId, newNickname);
        eventId = UUID.fromString(jdbc.queryForObject("""
                SELECT event_id FROM chat_nickname_outbox WHERE user_id = ?
                ORDER BY created_at DESC LIMIT 1
                """, String.class, actorId));

        verify(chatRoomClient, timeout(20_000)).updateParticipantNicknameVersioned(
                eq(actorId), eq(newNickname), eq(1L), any(UUID.class));
        verify(notifier, timeout(20_000)).notificationCreated(Set.of(friendId));
        assertThat(jdbc.queryForObject("SELECT status FROM chat_nickname_outbox WHERE event_id = ?",
                String.class, eventId.toString())).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id = ? "
                + "AND user_id = ? AND target_user_id = ? AND type = 'FRIEND_NICKNAME_CHANGED'",
                Integer.class, eventId.toString(), friendId, actorId)).isEqualTo(1);
    }

    /** 외래 키가 필요한 사용자 최소 행을 만들고 테스트 cleanup 목록에 기록한다. */
    private long createUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nickname = prefix.substring(0, 2) + suffix;
        String email = nickname + "@example.test";
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        userIds.add(userId);
        return userId;
    }
}
