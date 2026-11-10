package com.ktb4.team16.mulo.recommendation.client;

public class DailySongAiResponseException extends RuntimeException {
    private final Failure failure;

    public DailySongAiResponseException(Failure failure) {
        super("오늘의 노래 AI 추천 응답이 올바르지 않습니다: " + failure.name());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

    public enum Failure {
        EMPTY_RESULT, DEGRADED, INVALID_RESPONSE
    }
}
