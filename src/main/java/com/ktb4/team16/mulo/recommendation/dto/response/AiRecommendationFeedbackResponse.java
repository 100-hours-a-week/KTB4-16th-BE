package com.ktb4.team16.mulo.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedbackType;

public record AiRecommendationFeedbackResponse(String message, Data data) {

    public record Data(Long feedbackId, Long recommendationId, AiRecommendationFeedbackType feedback,
            @JsonInclude(JsonInclude.Include.NON_NULL) String selectedTrackId) { }
}
