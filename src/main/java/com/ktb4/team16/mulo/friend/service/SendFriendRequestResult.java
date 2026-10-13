package com.ktb4.team16.mulo.friend.service;

/** 요청 저장 또는 역방향 요청 자동 수락 결과를 구분한다. */
public sealed interface SendFriendRequestResult {

    /** 새 pending 친구 요청 ID를 전달한다. */
    record Pending(Long friendRequestId) implements SendFriendRequestResult {
    }

    /** 자동으로 성립한 친구 관계 ID를 전달한다. */
    record BecameFriends(Long friendshipId) implements SendFriendRequestResult {
    }
}
