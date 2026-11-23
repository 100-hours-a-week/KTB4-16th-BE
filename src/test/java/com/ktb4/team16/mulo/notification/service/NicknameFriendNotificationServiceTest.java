package com.ktb4.team16.mulo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.notification.exception.NicknameFriendshipSnapshotChangedException;
import com.ktb4.team16.mulo.notification.repository.NicknameFriendNotificationRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.outbox.NicknameFriendNotificationProperties;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.user.outbox.ChatNicknameChangedMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NicknameFriendNotificationServiceTest {
    private final NicknameFriendNotificationRepository notifications = mock();
    private final FriendshipRepository friendships = mock();
    private final UserRepository users = mock();
    private final NicknameFriendNotificationService service =
            new NicknameFriendNotificationService(notifications, friendships, users,
                    new NicknameFriendNotificationProperties(true, "2026-11-20T12:00:00+09:00"));

    /** 활성화 시각보다 오래된 Outbox 이벤트는 처리 이력만 남기고 알림을 건너뛴다. */
    @Test
    void suppressesEventsCreatedBeforeFeatureActivation() {
        var event = event(8, "new-name", "2026-11-20T11:59:59+09:00");
        when(notifications.claimEvent(event.eventId())).thenReturn(true);

        assertThat(service.process(event)).isEmpty();
    }

    /** 잠금 뒤 친구 목록에 새 사용자가 추가됐으면 전체 작업을 재시도한다. */
    @Test
    void retriesWhenFriendListChangesWhileAcquiringUserLocks() {
        var event = event(8, "new-name", "2026-11-20T12:01:00+09:00");
        var actor = user(10L, "new-name", 8L);
        var firstFriend = user(2L, "first", 0L);
        when(notifications.claimEvent(event.eventId())).thenReturn(true);
        when(friendships.findActiveFriendUserIds(10L)).thenReturn(List.of(2L));
        when(users.lockActiveUsersByIdAscending(List.of(2L, 10L))).thenReturn(List.of(firstFriend, actor));
        when(friendships.findActiveFriendUserIdsForUpdate(10L)).thenReturn(List.of(2L, 3L));

        assertThatThrownBy(() -> service.process(event))
                .isInstanceOf(NicknameFriendshipSnapshotChangedException.class);
    }

    /** 이벤트 시각과 동일한 메시지 필드를 가진 테스트 입력을 만든다. */
    private ChatNicknameChangedMessage event(long version, String nickname, String occurredAt) {
        return new ChatNicknameChangedMessage(1, UUID.randomUUID(), "USER_NICKNAME_CHANGED",
                10L, nickname, version, occurredAt);
    }

    /** 잠금 조회 결과에 사용할 ID·닉네임·버전의 사용자 대역을 만든다. */
    private User user(Long userId, String nickname, long version) {
        User user = mock();
        when(user.getUserId()).thenReturn(userId);
        when(user.getNickname()).thenReturn(nickname);
        when(user.getNicknameVersion()).thenReturn(version);
        return user;
    }
}
