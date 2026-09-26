package com.ktb4.team16.mulo.recommendation.message;

public enum RecommendationMessage {
    PLAYLIST_RETRIEVED("현재 추천 플레이리스트 조회 성공"),
    PLAYLIST_CREATED("추천 플레이리스트 생성 성공");

    private final String message;

    RecommendationMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
