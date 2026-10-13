package com.ktb4.team16.mulo.friend.dto.response;

/** 보낸 요청 목록 조회의 HTTP envelope다. */
public record SentFriendRequestsResponse(String message, SentFriendRequestsData data) {
}
