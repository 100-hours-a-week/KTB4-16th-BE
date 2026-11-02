package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 한 서버 인스턴스에서 사용자별 AI 호출을 하나로 제한한다. */
@Component
@ConditionalOnProperty(prefix = "memory-search", name = "conversation-store",
        havingValue = "in-memory", matchIfMissing = true)
public class MemorySearchInFlightRegistry implements MemorySearchRequestRegistry {

    private final ConcurrentHashMap<Long, Object> activeRequests = new ConcurrentHashMap<>();

    /** 이미 호출 중인 사용자에게는 빈 값을, 새 호출에는 해제 가능한 점유권을 반환한다. */
    @Override
    public Optional<Lease> tryAcquire(Long userId) {
        Object token = new Object();
        if (activeRequests.putIfAbsent(userId, token) != null) {
            return Optional.empty();
        }
        return Optional.of(new Lease(userId, token));
    }

    /** 자신이 획득한 점유권만 한 번 해제한다. */
    public final class Lease implements MemorySearchRequestLease {
        private final Long userId;
        private final Object token;
        private final AtomicBoolean closed = new AtomicBoolean();

        /** 사용자와 자신이 발급받은 메모리 토큰을 함께 보관한다. */
        private Lease(Long userId, Object token) {
            this.userId = userId;
            this.token = token;
        }

        /** 인메모리 점유권을 가진 요청만 기존 문맥 추가 계약을 호출한다. */
        @Override
        public boolean appendIfActive(MemorySearchConversationStore store, String conversationId,
                MemorySearchTurn turn) {
            if (closed.get() || activeRequests.get(userId) != token) {
                throw new IllegalStateException("Memory search request lease is no longer valid");
            }
            return store.appendIfActive(userId, conversationId, turn);
        }

        /** AI 호출의 성공·실패·시간 초과 후에도 점유권을 안전하게 해제한다. */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                activeRequests.remove(userId, token);
            }
        }
    }
}
