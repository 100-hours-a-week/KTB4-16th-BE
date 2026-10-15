package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 검색 질문이 유효한 대화인지 확인하고 사용자별 AI 호출 점유권을 발급한다. */
@Component
@RequiredArgsConstructor
public class MemorySearchRequestGate {

    private final MemorySearchConversationStore store;
    private final MemorySearchInFlightRegistry registry;

    /** 닫힌 대화는 410, 이미 기다리는 사용자는 409를 반환하고 나머지는 점유권을 준다. */
    public MemorySearchInFlightRegistry.Lease acquire(Long userId, String conversationId) {
        if (!store.isActive(userId, conversationId)) {
            throw new MemorySearchDomainException(
                    ErrorCode.MEMORY_SEARCH_CONVERSATION_GONE);
        }
        return registry.tryAcquire(userId).orElseThrow(() ->
                new MemorySearchDomainException(ErrorCode.MEMORY_SEARCH_IN_PROGRESS));
    }
}
