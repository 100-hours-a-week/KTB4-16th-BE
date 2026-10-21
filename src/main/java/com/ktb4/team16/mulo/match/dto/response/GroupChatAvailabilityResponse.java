package com.ktb4.team16.mulo.match.dto.response;

public record GroupChatAvailabilityResponse(String message, Data data) {

    public record Data(boolean available) { }
}
