package com.ktb4.team16.mulo.match.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GroupChatJoinRequest(@NotNull @Positive Long voteAnswerId) {
}
