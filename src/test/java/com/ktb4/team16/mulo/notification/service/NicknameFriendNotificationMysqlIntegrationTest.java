package com.ktb4.team16.mulo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.notification.entity.NotificationType;
import com.ktb4.team16.mulo.notification.service.NotificationService;
import com.ktb4.team16.mulo.user.outbox.ChatNicknameChangedMessage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** MySQL에서 친구 스냅샷 잠금, 이벤트 멱등성, 버전 순서와 트랜잭션 rollback을 검증한다. */
@SpringBootTest(properties = {
        "mulo.rabbitmq.enabled=false",
        "mulo.nickname.dispatch.enabled=false",
        "mulo.nickname.friend-notifications.enabled=true",
        "mulo.nickname.friend-notifications.enabled-at=2026-11-20T00:00:00Z"
})
class NicknameFriendNotificationMysqlIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NicknameFriendNotificationService service;
    @Autowired private NotificationService notificationService;

    private final List<Long> userIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();

    /** 테스트 알림, 멱등 처리 행, 친구 관계와 사용자만 제거한다. */
    @AfterEach
    void cleanUp() {
        for (UUID eventId : eventIds) {
            jdbc.update("DELETE FROM notifications WHERE source_event_id = ?", eventId.toString());
            jdbc.update("DELETE FROM nickname_notification_events WHERE event_id = ?", eventId.toString());
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM friendships WHERE user_low_id = ? OR user_high_id = ?", userId, userId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    /** 활성 친구만 알림을 받고 알림 삭제 뒤 재전달도 멱등 이력이 막는다. */
    @Test
    void savesForActiveFriendsAndDoesNotRecreateDeletedNotificationsOnRedelivery() {
        TestUser actor = createUser("actor");
        TestUser firstFriend = createUser("first");
        TestUser secondFriend = createUser("second");
        TestUser withdrawnFriend = createUser("withdrawn");
        createFriendship(actor.id(), firstFriend.id());
        createFriendship(actor.id(), secondFriend.id());
        createFriendship(actor.id(), withdrawnFriend.id());
        jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP(6) WHERE user_id = ?", withdrawnFriend.id());
        setNickname(actor.id(), "new-name", 8L);
        ChatNicknameChangedMessage event = event(actor.id(), "new-name", 8L);

        assertThat(service.process(event)).containsExactlyInAnyOrder(firstFriend.id(), secondFriend.id());
        assertThat(jdbc.queryForList("SELECT user_id FROM notifications WHERE source_event_id = ? ORDER BY user_id",
                Long.class, event.eventId().toString())).containsExactly(firstFriend.id(), secondFriend.id());
        assertThat(jdbc.queryForList("SELECT target_user_id FROM notifications WHERE source_event_id = ?",
                Long.class, event.eventId().toString()))
                .containsExactlyInAnyOrder(actor.id(), actor.id());
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE source_event_id = ? LIMIT 1",
                String.class, event.eventId().toString())).isEqualTo("‘new-name’님이 닉네임을 변경했어요.");
        var apiNotification = notificationService.getNotifications(firstFriend.id(), null, 20)
                .data().notifications().stream()
                .filter(item -> item.type() == NotificationType.FRIEND_NICKNAME_CHANGED)
                .findFirst().orElseThrow();
        assertThat(apiNotification.targetId()).isEqualTo(actor.id());
        assertThat(apiNotification.targetType()).isEqualTo("USER");

        jdbc.update("DELETE FROM notifications WHERE source_event_id = ?", event.eventId().toString());

        assertThat(service.process(event)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id = ?",
                Integer.class, event.eventId().toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nickname_notification_events WHERE event_id = ?",
                Integer.class, event.eventId().toString())).isEqualTo(1);
    }

    /** 현재 닉네임 버전 8 뒤 도착한 버전 7은 친구 알림으로 저장하지 않는다. */
    @Test
    void ignoresOlderEventAfterLatestNicknameVersion() {
        TestUser actor = createUser("ordered");
        TestUser friend = createUser("friend");
        createFriendship(actor.id(), friend.id());
        setNickname(actor.id(), "version-8", 8L);
        ChatNicknameChangedMessage latest = event(actor.id(), "version-8", 8L);
        ChatNicknameChangedMessage old = event(actor.id(), "version-7", 7L);

        assertThat(service.process(latest)).containsExactly(friend.id());
        assertThat(service.process(old)).isEmpty();
        assertThat(jdbc.queryForList("SELECT content FROM notifications WHERE user_id = ? ORDER BY notification_id",
                String.class, friend.id())).containsExactly("‘version-8’님이 닉네임을 변경했어요.");
    }

    /** 변경한 사용자가 물리 삭제돼도 알림 행은 남고 대상 FK만 null이 된다. */
    @Test
    void keepsNotificationWhenChangedUserIsDeleted() {
        TestUser actor = createUser("deletedActor");
        TestUser friend = createUser("recipient");
        createFriendship(actor.id(), friend.id());
        setNickname(actor.id(), "new-name", 1L);
        ChatNicknameChangedMessage event = event(actor.id(), "new-name", 1L);

        assertThat(service.process(event)).containsExactly(friend.id());
        jdbc.update("DELETE FROM friendships WHERE user_low_id = ? OR user_high_id = ?", actor.id(), actor.id());
        jdbc.update("DELETE FROM users WHERE user_id = ?", actor.id());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id = ?",
                Integer.class, event.eventId().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT target_user_id FROM notifications WHERE source_event_id = ?",
                Long.class, event.eventId().toString())).isNull();
        var notification = notificationService.getNotifications(friend.id(), null, 20)
                .data().notifications().getFirst();
        assertThat(notification.targetId()).isNull();
        assertThat(notification.targetType()).isEqualTo("USER");
    }

    /** 알림 INSERT 실패 시 같은 트랜잭션에서 선점한 이벤트 이력도 rollback한다. */
    @Test
    void rollsBackEventClaimWhenRecipientInsertFails() {
        TestUser actor = createUser("rollback");
        TestUser friend = createUser("recipient");
        createFriendship(actor.id(), friend.id());
        setNickname(actor.id(), "new-name", 1L);
        ChatNicknameChangedMessage event = event(actor.id(), "new-name", 1L);
        jdbc.update("""
                INSERT INTO notifications (user_id, type, source_event_id, title, content)
                VALUES (?, 'FRIEND_REQUEST', ?, 'existing', 'existing')
                """, friend.id(), event.eventId().toString());

        assertThatThrownBy(() -> service.process(event)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nickname_notification_events WHERE event_id = ?",
                Integer.class, event.eventId().toString())).isZero();
        jdbc.update("DELETE FROM notifications WHERE source_event_id = ?", event.eventId().toString());

        assertThat(service.process(event)).containsExactly(friend.id());
    }

    /** FK를 만족하는 활성 사용자를 만들고 cleanup 목록에 등록한다. */
    private TestUser createUser(String prefix) {
        String nickname = prefix.substring(0, Math.min(prefix.length(), 2))
                + UUID.randomUUID().toString().substring(0, 8);
        String email = nickname + "@example.test";
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        Long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        userIds.add(userId);
        return new TestUser(userId, nickname);
    }

    /** 정렬된 사용자 쌍으로 실제 친구 관계를 저장한다. */
    private void createFriendship(Long firstUserId, Long secondUserId) {
        long lowId = Math.min(firstUserId, secondUserId);
        long highId = Math.max(firstUserId, secondUserId);
        jdbc.update("INSERT INTO friendships (user_low_id, user_high_id) VALUES (?, ?)", lowId, highId);
    }

    /** 테스트 중인 사용자의 현재 닉네임과 동기화 버전을 직접 준비한다. */
    private void setNickname(Long userId, String nickname, long version) {
        jdbc.update("UPDATE users SET nickname = ?, nickname_version = ? WHERE user_id = ?",
                nickname, version, userId);
    }

    /** 테스트 시각은 활성화 컷오프 이후로 고정한다. */
    private ChatNicknameChangedMessage event(Long userId, String nickname, long version) {
        UUID eventId = UUID.randomUUID();
        eventIds.add(eventId);
        return new ChatNicknameChangedMessage(1, eventId, "USER_NICKNAME_CHANGED", userId,
                nickname, version, OffsetDateTime.parse("2026-11-20T12:01:00+09:00").toString());
    }

    /** 테스트에서 생성한 ID와 닉네임을 함께 보유한다. */
    private record TestUser(Long id, String nickname) {
    }
}
