package com.ktb4.team16.mulo.user.outbox;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 변경 당시 닉네임과 사용자별 버전을 보존하는 채팅 동기화 메시지다. */
public record ChatNicknameChangedMessage(
        int schemaVersion,
        UUID eventId,
        String eventType,
        Long userId,
        String nickname,
        long nicknameVersion,
        String occurredAt) {

    /** 새 이벤트 ID를 발급하고 발생 시각을 오프셋을 포함한 ISO 형식으로 고정한다. */
    public static ChatNicknameChangedMessage create(
            Long userId, String nickname, long nicknameVersion, OffsetDateTime occurredAt) {
        return new ChatNicknameChangedMessage(1, UUID.randomUUID(), "USER_NICKNAME_CHANGED",
                userId, nickname, nicknameVersion,
                occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
