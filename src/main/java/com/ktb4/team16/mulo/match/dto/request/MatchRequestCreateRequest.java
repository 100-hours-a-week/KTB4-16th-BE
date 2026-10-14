package com.ktb4.team16.mulo.match.dto.request;

import jakarta.validation.constraints.NotNull;

public record MatchRequestCreateRequest(
        @NotNull(message = "INVALID_INPUT_VALUE") Long voteAnswerId
) { }
