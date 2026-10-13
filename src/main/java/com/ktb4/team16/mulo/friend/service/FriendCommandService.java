package com.ktb4.team16.mulo.friend.service;

import com.ktb4.team16.mulo.notification.outbox.NotificationCommandResult;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxImmediateDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 친구 명령의 트랜잭션 완료 뒤 알림 처리를 이어 주는 응용 서비스다. */
@Service
@RequiredArgsConstructor
public class FriendCommandService {

    private final FriendCommandTransactionalService commands;
    private final NotificationOutboxImmediateDispatcher outboxDispatcher;

    /** 친구 요청 transaction이 반환된 뒤 요청 알림을 동기 처리한다. */
    public SendFriendRequestResult sendFriendRequest(Long currentUserId, String nickname) {
        NotificationCommandResult<SendFriendRequestResult> outcome =
                commands.sendFriendRequest(currentUserId, nickname);
        outboxDispatcher.processAfterTransaction(outcome.eventIds());
        return outcome.result();
    }

    /** 친구 수락 transaction이 반환된 뒤 수락 알림을 동기 처리한다. */
    public Long acceptFriendRequest(Long currentUserId, Long friendRequestId) {
        NotificationCommandResult<Long> outcome =
                commands.acceptFriendRequest(currentUserId, friendRequestId);
        outboxDispatcher.processAfterTransaction(outcome.eventIds());
        return outcome.result();
    }

    /** 요청 상태 변경을 transaction worker에 위임한다. */
    public void deleteFriendRequest(Long currentUserId, Long friendRequestId) {
        commands.deleteFriendRequest(currentUserId, friendRequestId);
    }

    /** 친구 관계 삭제를 transaction worker에 위임한다. */
    public void deleteFriendship(Long currentUserId, Long friendshipId) {
        commands.deleteFriendship(currentUserId, friendshipId);
    }
}
