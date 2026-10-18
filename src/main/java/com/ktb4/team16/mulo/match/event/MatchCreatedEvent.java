package com.ktb4.team16.mulo.match.event;

import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;

/** Commit된 매칭의 불변 채팅방 생성 요청을 전달한다. */
public record MatchCreatedEvent(Long matchId, ChatRoomCreateRequest chatRoomRequest) {
}
