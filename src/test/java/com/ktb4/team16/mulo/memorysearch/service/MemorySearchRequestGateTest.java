package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemorySearchRequestGateTest {

    @Mock
    private MemorySearchConversationStore store;

    /** 닫혔거나 유휴 만료된 대화는 AI 호출 점유권을 얻지 못하고 410을 낸다. */
    @Test
    void rejectsGoneConversationBeforeReservingAnAiCall() {
        MemorySearchInFlightRegistry registry = new MemorySearchInFlightRegistry();
        MemorySearchRequestGate gate = new MemorySearchRequestGate(store, registry);
        when(store.isActive(35L, "expired")).thenReturn(false);

        assertThatThrownBy(() -> gate.acquire(35L, "expired"))
                .isInstanceOfSatisfying(MemorySearchDomainException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(ErrorCode.MEMORY_SEARCH_CONVERSATION_GONE));
        try (var afterFailure = registry.tryAcquire(35L).orElseThrow()) {
            assertThat(afterFailure).isNotNull();
        }
    }

    /** 같은 사용자가 기다리는 동안의 다음 요청은 409를 내고 다른 사용자는 독립적이다. */
    @Test
    void rejectsConcurrentQuestionForSameUserUntilLeaseCloses() {
        MemorySearchRequestGate gate = new MemorySearchRequestGate(store,
                new MemorySearchInFlightRegistry());
        when(store.isActive(35L, "conversation-a")).thenReturn(true);
        when(store.isActive(36L, "conversation-b")).thenReturn(true);

        try (var first = gate.acquire(35L, "conversation-a")) {
            assertThatThrownBy(() -> gate.acquire(35L, "conversation-a"))
                    .isInstanceOfSatisfying(MemorySearchDomainException.class,
                            error -> assertThat(error.errorCode())
                                    .isEqualTo(ErrorCode.MEMORY_SEARCH_IN_PROGRESS));
            try (var anotherUser = gate.acquire(36L, "conversation-b")) {
                assertThat(anotherUser).isNotNull();
            }
        }

        try (var next = gate.acquire(35L, "conversation-a")) {
            assertThat(next).isNotNull();
        }
    }

    /** 다른 사용자의 대화 ID는 본인 키로 조회되어 410을 반환한다. */
    @Test
    void cannotUseAnotherUsersConversationId() {
        MemorySearchRequestGate gate = new MemorySearchRequestGate(store,
                new MemorySearchInFlightRegistry());
        when(store.isActive(36L, "owned-by-35")).thenReturn(false);

        assertThatThrownBy(() -> gate.acquire(36L, "owned-by-35"))
                .isInstanceOfSatisfying(MemorySearchDomainException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(ErrorCode.MEMORY_SEARCH_CONVERSATION_GONE));
    }
}
