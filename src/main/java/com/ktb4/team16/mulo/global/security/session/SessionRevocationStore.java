package com.ktb4.team16.mulo.global.security.session;

import java.time.Duration;

/** 로그아웃 세션과 레거시 Access Token의 Redis 차단 상태를 관리한다. */
public interface SessionRevocationStore {

    /** 세션과 레거시 사용자 표식을 지정된 유효기간으로 원자적으로 저장한다. */
    void revokeSession(String sessionId, Duration sessionTtl, Long legacyUserId, Duration legacyTtl);

    /** 지정 세션이 로그아웃으로 차단됐는지 확인한다. */
    boolean isSessionRevoked(String sessionId);

    /** 지정 사용자의 세션 ID 없는 레거시 Access Token이 차단됐는지 확인한다. */
    boolean areLegacyUserTokensRevoked(Long userId);
}
