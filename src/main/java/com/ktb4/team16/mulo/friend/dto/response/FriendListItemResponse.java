package com.ktb4.team16.mulo.friend.dto.response;

/** 친구 관계와 상대 사용자의 표시 정보를 반환한다. */
public record FriendListItemResponse(Long friendshipId, Long userId, String nickname) {
}
