package com.ktb4.team16.mulo.user.message;

public enum UserMessage {
    SIGNUP_COMPLETED("회원가입이 완료되었습니다."),
    PROFILE_RETRIEVED("회원 정보 조회 성공");

    private final String message;

    UserMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
