package com.ktb4.team16.mulo.friend.dto.response;

/** 수락으로 성립한 친구 관계 ID와 성공 메시지를 반환한다. */
public record FriendRequestAcceptedData(Long friendshipId, String message) {
}
