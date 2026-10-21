package com.ktb4.team16.mulo.groupchat.config;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mulo.group-chat")
public record GroupChatProperties(boolean enabled, LocalDate startDate) {

    public GroupChatProperties {
        if (startDate == null) {
            throw new IllegalArgumentException("그룹 채팅 시작일이 필요합니다.");
        }
    }

    public boolean hasTransitionStarted(LocalDate date) {
        return !date.isBefore(startDate);
    }

    public boolean isAvailableOn(LocalDate date) {
        return enabled && hasTransitionStarted(date);
    }
}
