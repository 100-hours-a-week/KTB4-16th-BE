package com.ktb4.team16.mulo.vote.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record VoteAnswerSubmitRequest(
        @NotNull(message = "SELECTED_OPTION_REQUIRED")
        @Pattern(regexp = "A|B", message = "SELECTED_OPTION_REQUIRED") String selectedOption
) { }
