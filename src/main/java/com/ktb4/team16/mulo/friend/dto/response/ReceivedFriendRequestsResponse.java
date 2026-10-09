package com.ktb4.team16.mulo.friend.dto.response;

/** 받은 요청 목록 조회의 HTTP envelope다. */
public record ReceivedFriendRequestsResponse(String message, ReceivedFriendRequestsData data) {
}
