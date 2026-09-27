package com.ktb4.team16.mulo.recommendation.dto.request;

import jakarta.validation.constraints.NotNull;

public record PhotoRecommendationRequest(
        @NotNull(message = "INVALID_INPUT_VALUE") Long uploadId
) {
}
