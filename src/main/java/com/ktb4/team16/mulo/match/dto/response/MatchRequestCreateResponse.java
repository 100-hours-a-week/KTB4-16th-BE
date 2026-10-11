package com.ktb4.team16.mulo.match.dto.response;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;

public record MatchRequestCreateResponse(String message, Data data) {

    public record Data(Long matchRequestId, MatchRequestStatus status) { }
}
