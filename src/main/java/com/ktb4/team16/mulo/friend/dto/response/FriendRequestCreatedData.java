package com.ktb4.team16.mulo.friend.dto.response;

/** 새로 생성한 pending 요청 ID와 성공 메시지를 반환한다. */
public record FriendRequestCreatedData(Long friendRequestId, String message) {
}
