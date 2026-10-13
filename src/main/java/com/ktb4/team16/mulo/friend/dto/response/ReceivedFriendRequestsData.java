package com.ktb4.team16.mulo.friend.dto.response;

import java.util.List;

/** 받은 친구 요청 목록과 다음 페이지 커서를 감싼다. */
public record ReceivedFriendRequestsData(List<ReceivedFriendRequestItem> requests, String nextCursor) {
}
