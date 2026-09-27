package com.ktb4.team16.mulo.recommendation.client;

public class PhotoRecommendationAiException extends RuntimeException {
    public PhotoRecommendationAiException() {
        super("Photo recommendation AI service is unavailable");
    }

    public PhotoRecommendationAiException(Throwable cause) {
        super("Photo recommendation AI service is unavailable", cause);
    }
}
