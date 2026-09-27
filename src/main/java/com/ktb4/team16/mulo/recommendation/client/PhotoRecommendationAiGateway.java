package com.ktb4.team16.mulo.recommendation.client;

public interface PhotoRecommendationAiGateway {
    PhotoRecommendationAiClient.PhotoRecommendationAiResponse recommend(String imageUrl);
}
