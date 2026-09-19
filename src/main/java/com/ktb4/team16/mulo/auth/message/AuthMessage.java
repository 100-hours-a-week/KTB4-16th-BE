package com.ktb4.team16.mulo.auth.message;

public enum AuthMessage {
    LOGIN_COMPLETED("로그인 완료"),
    LOGOUT_COMPLETED("로그아웃되었습니다.");

    private final String message;

    AuthMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
