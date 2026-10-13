package com.ktb4.team16.mulo.friend.repository;

import java.time.LocalDateTime;

/** 받은·보낸 요청 목록 native query가 반환하는 최소 projection이다. */
public interface FriendRequestListRow {
    Long getFriendRequestId();

    Long getUserId();

    String getNickname();

    LocalDateTime getCreatedAt();
}
