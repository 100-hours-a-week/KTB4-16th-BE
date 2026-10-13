package com.ktb4.team16.mulo.match.dto.response;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.entity.MatchRequest;

public record MatchRequestStatusResponse(String message, Data data) {

    public static MatchRequestStatusResponse from(String message, MatchRequest request) {
        Long chatRoomId = request.getStatus() == MatchRequestStatus.MATCHED && request.getMatch() != null
                ? request.getMatch().getChatRoomId() : null;
        return new MatchRequestStatusResponse(message,
                new Data(request.getMatchRequestId(), request.getStatus(), chatRoomId));
    }

    public record Data(Long matchRequestId, MatchRequestStatus status, Long chatRoomId) { }
}
