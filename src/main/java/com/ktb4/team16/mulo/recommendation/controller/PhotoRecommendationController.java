package com.ktb4.team16.mulo.recommendation.controller;

import com.ktb4.team16.mulo.recommendation.dto.request.PhotoRecommendationRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.PhotoRecommendationResponse;
import com.ktb4.team16.mulo.recommendation.service.PhotoRecommendationService;
import com.ktb4.team16.mulo.recommendation.message.RecommendationMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations/photo")
@RequiredArgsConstructor
public class PhotoRecommendationController {
    private final PhotoRecommendationService photoRecommendationService;

    @PostMapping
    public PhotoRecommendationResponse recommend(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PhotoRecommendationRequest request) {
        return new PhotoRecommendationResponse(RecommendationMessage.PHOTO_RECOMMENDATION_SUCCEEDED.message(),
                photoRecommendationService.recommend(userId, request.uploadId()));
    }
}
