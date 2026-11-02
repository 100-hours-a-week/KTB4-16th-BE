package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;

/** 요청 점유권이 유효한 경우에만 완료 결과를 반영하고 자신만 해제한다. */
public interface MemorySearchRequestLease extends AutoCloseable {
    /** 활성 대화에 결과를 추가하며 소유권을 잃은 요청은 예외로 거절한다. */
    boolean appendIfActive(MemorySearchConversationStore store, String conversationId, MemorySearchTurn turn);

    /** 획득한 점유권을 한 번 해제하며 다른 요청의 권한을 지우지 않는다. */
    @Override
    void close();
}
