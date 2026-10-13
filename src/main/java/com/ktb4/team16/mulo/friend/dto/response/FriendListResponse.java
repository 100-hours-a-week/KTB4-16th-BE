package com.ktb4.team16.mulo.friend.dto.response;

/** 친구 목록 조회의 HTTP envelope다. */
public record FriendListResponse(String message, FriendListData data) {
}
