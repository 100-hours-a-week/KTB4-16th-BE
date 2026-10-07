package com.ktb4.team16.mulo.friend.dto.response;

import java.util.List;

/** 친구 목록과 다음 페이지 커서를 감싼다. */
public record FriendListData(List<FriendListItemResponse> friends, String nextCursor) {
}
