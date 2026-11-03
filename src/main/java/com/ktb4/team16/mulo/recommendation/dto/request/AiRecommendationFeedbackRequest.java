package com.ktb4.team16.mulo.recommendation.dto.request;

import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedbackType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiRecommendationFeedbackRequest(
        @NotNull(message = "INVALID_INPUT_VALUE") AiRecommendationFeedbackType feedback,
        @Size(max = 22, message = "INVALID_INPUT_VALUE")
        @Pattern(regexp = "\\S+", message = "INVALID_INPUT_VALUE") String selectedTrackId) {
}
