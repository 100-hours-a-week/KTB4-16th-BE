package com.ktb4.team16.mulo.friend.repository;

/** 친구 목록 native query가 반환하는 최소 정렬·표시 projection이다. */
public interface FriendListRow {
    Long getFriendshipId();

    Long getUserId();

    String getNickname();
}
