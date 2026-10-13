package com.ktb4.team16.mulo.friend.dto.response;

/** 역방향 요청 자동 수락 응답 envelope다. */
public record FriendRequestAutoAcceptedResponse(
        String message,
        FriendRequestAutoAcceptedData data
) {
}
