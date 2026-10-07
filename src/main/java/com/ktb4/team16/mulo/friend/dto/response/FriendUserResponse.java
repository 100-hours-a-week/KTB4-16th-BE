package com.ktb4.team16.mulo.friend.dto.response;

/** 요청 목록에서 상대 사용자의 ID와 닉네임만 반환한다. */
public record FriendUserResponse(Long userId, String nickname) {
}
