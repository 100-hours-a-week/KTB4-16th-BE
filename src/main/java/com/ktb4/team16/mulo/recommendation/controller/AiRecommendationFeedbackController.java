package com.ktb4.team16.mulo.recommendation.controller;

import com.ktb4.team16.mulo.recommendation.dto.request.AiRecommendationFeedbackRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.AiRecommendationFeedbackResponse;
import com.ktb4.team16.mulo.recommendation.service.AiRecommendationFeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-recommendations/{recommendationId}/feedback")
@RequiredArgsConstructor
public class AiRecommendationFeedbackController {

    private final AiRecommendationFeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AiRecommendationFeedbackResponse submit(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive(message = "INVALID_INPUT_VALUE") Long recommendationId,
            @Valid @RequestBody AiRecommendationFeedbackRequest request) {
        return feedbackService.submit(userId, recommendationId, request);
    }
}
