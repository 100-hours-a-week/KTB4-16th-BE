package com.ktb4.team16.mulo.friend.dto.response;

/** 역방향 요청 처리로 성립한 친구 관계 ID와 성공 메시지를 반환한다. */
public record FriendRequestAutoAcceptedData(Long friendshipId, String message) {
}
