package com.ktb4.team16.mulo.place.message;

public enum PlaceMessage {
    MY_PLACES_RETRIEVED("내 자물쇠 조회 성공");

    private final String message;

    PlaceMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
