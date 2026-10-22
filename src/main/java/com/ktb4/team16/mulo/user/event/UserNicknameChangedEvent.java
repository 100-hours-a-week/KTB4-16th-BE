package com.ktb4.team16.mulo.user.event;

/** 닉네임 변경 트랜잭션이 커밋된 뒤 동기화를 시작할 사용자 식별자만 전달한다. */
public record UserNicknameChangedEvent(Long userId) {
}
