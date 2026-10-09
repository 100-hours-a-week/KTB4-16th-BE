package com.ktb4.team16.mulo.friend.dto.response;

/** 새 친구 요청 응답 envelope다. */
public record FriendRequestCreatedResponse(String message, FriendRequestCreatedData data) {
}
