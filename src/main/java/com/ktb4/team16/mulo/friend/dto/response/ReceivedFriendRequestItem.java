package com.ktb4.team16.mulo.friend.dto.response;

import java.time.LocalDateTime;

/** 받은 요청 목록의 단일 발신자와 생성 시각을 반환한다. */
public record ReceivedFriendRequestItem(
        Long friendRequestId,
        FriendUserResponse requester,
        LocalDateTime createdAt
) {
}
