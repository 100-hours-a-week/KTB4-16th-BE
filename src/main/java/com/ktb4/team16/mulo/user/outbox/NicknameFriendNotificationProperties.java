package com.ktb4.team16.mulo.user.outbox;

import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 친구 닉네임 알림의 명시적 활성화와 과거 이벤트 억제 시각을 보유한다. */
@ConfigurationProperties(prefix = "mulo.nickname.friend-notifications")
public record NicknameFriendNotificationProperties(boolean enabled, String enabledAt) {

    /** 알림을 켤 때 모든 인스턴스가 공유할 활성화 시각이 설정됐는지 검사한다. */
    public NicknameFriendNotificationProperties {
        if (enabled && (enabledAt == null || enabledAt.isBlank())) {
            throw new IllegalStateException("MULO_NICKNAME_FRIEND_NOTIFICATIONS_ENABLED_AT 설정이 필요합니다.");
        }
        if (enabled) {
            OffsetDateTime.parse(enabledAt);
        }
    }

    /** 사전 유효성 검사를 마친 공통 활성화 시각을 반환한다. */
    public OffsetDateTime activationTime() {
        return OffsetDateTime.parse(enabledAt);
    }
}
