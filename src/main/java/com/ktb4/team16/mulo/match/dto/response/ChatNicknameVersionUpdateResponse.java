package com.ktb4.team16.mulo.match.dto.response;

/** Chat이 닉네임 이벤트를 신규 적용·중복·과거 중 어느 상태로 처리했는지 나타낸다. */
public record ChatNicknameVersionUpdateResponse(String status, int updatedParticipantCount) {
}
