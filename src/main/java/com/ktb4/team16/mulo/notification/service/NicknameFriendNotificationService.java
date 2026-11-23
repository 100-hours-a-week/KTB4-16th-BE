package com.ktb4.team16.mulo.notification.service;

import com.ktb4.team16.mulo.friend.repository.FriendshipRepository;
import com.ktb4.team16.mulo.notification.exception.NicknameFriendshipSnapshotChangedException;
import com.ktb4.team16.mulo.notification.repository.NicknameFriendNotificationRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.outbox.ChatNicknameChangedMessage;
import com.ktb4.team16.mulo.user.outbox.NicknameFriendNotificationProperties;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수신자 잠금, 최신 닉네임 판정, 알림 INSERT를 한 DB 트랜잭션으로 수행한다. */
@Service
@ConditionalOnProperty(name = "mulo.nickname.friend-notifications.enabled", havingValue = "true")
public class NicknameFriendNotificationService {
    private final NicknameFriendNotificationRepository notifications;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final OffsetDateTime activationTime;

    /** 메시지 처리에 필요한 저장소와 공통 활성화 시각을 연결한다. */
    public NicknameFriendNotificationService(NicknameFriendNotificationRepository notifications,
            FriendshipRepository friendships, UserRepository users,
            NicknameFriendNotificationProperties properties) {
        this.notifications = notifications;
        this.friendships = friendships;
        this.users = users;
        this.activationTime = properties.activationTime();
    }

    /** 중복·과거·오래된 이벤트는 건너뛰고 새로 저장한 수신자만 반환한다. */
    @Transactional
    public Set<Long> process(ChatNicknameChangedMessage event) {
        if (!notifications.claimEvent(event.eventId())) {
            return Set.of();
        }
        if (OffsetDateTime.parse(event.occurredAt()).isBefore(activationTime)) {
            return Set.of();
        }

        List<Long> initialFriendIds = friendships.findActiveFriendUserIds(event.userId());
        List<Long> userIdsToLock = new ArrayList<>(new LinkedHashSet<>(initialFriendIds));
        userIdsToLock.add(event.userId());
        List<Long> sortedUserIds = userIdsToLock.stream().distinct().sorted().toList();
        List<User> lockedUsers = users.lockActiveUsersByIdAscending(sortedUserIds);
        Map<Long, User> activeUsers = lockedUsers.stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        User changedUser = activeUsers.get(event.userId());
        if (changedUser == null || changedUser.getNicknameVersion() > event.nicknameVersion()) {
            return Set.of();
        }
        if (changedUser.getNicknameVersion() < event.nicknameVersion()
                || !changedUser.getNickname().equals(event.nickname())) {
            throw new IllegalStateException("NICKNAME_EVENT_DOES_NOT_MATCH_CURRENT_USER");
        }

        List<Long> currentFriendIds = friendships.findActiveFriendUserIdsForUpdate(event.userId());
        if (!new LinkedHashSet<>(sortedUserIds).containsAll(currentFriendIds)) {
            // 관계가 바뀐 트랜잭션을 기다렸다가 같은 이벤트를 최신 수신자 집합으로 다시 평가한다.
            throw new NicknameFriendshipSnapshotChangedException();
        }

        Set<Long> createdRecipientIds = new LinkedHashSet<>();
        for (Long friendId : currentFriendIds) {
            if (activeUsers.containsKey(friendId)
                    && notifications.insertNotification(event.eventId(), friendId,
                            event.userId(), event.nickname()) > 0) {
                createdRecipientIds.add(friendId);
            }
        }
        return Set.copyOf(createdRecipientIds);
    }
}
