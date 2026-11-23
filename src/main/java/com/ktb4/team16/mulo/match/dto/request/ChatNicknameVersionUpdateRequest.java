package com.ktb4.team16.mulo.match.dto.request;

import java.util.UUID;

/** Chat의 버전 적용 내부 API에 필요한 닉네임 이벤트 값이다. */
public record ChatNicknameVersionUpdateRequest(String nickname, long nicknameVersion, UUID eventId) {
}
