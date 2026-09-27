package com.ktb4.team16.mulo.recommendation.client;

// AI Gateway 통신 및 응답 계약 위반을 외부에 안전한 오류로 전달한다.
public class RecommendationAiException extends RuntimeException {
    public RecommendationAiException() {
        super("Context recommendation AI service is unavailable");
    }

    public RecommendationAiException(Throwable cause) {
        super("Context recommendation AI service is unavailable", cause);
    }
}
