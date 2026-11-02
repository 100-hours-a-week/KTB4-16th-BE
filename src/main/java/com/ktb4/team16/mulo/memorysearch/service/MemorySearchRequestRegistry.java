package com.ktb4.team16.mulo.memorysearch.service;

import java.util.Optional;

/** 저장 방식과 무관하게 사용자별 AI 요청 점유권을 발급한다. */
public interface MemorySearchRequestRegistry {
    /** 이미 점유 중이면 빈 값을 반환하고 저장소 장애는 예외로 구분한다. */
    Optional<? extends MemorySearchRequestLease> tryAcquire(Long userId);
}
