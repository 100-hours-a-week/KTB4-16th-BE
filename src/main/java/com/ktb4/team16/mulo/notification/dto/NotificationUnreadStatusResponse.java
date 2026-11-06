package com.ktb4.team16.mulo.notification.dto;

public record NotificationUnreadStatusResponse(String message, Data data) {
    public record Data(boolean hasUnread) {
    }
}
