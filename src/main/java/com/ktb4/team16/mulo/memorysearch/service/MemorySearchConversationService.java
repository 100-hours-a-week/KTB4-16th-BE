package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 인증 사용자의 대화 시작과 명시적 종료를 대화 저장소에 위임한다. */
@Service
@RequiredArgsConstructor
public class MemorySearchConversationService {

    private final MemorySearchConversationStore store;

    /** 서버가 새 대화 ID를 발급하고 사용자 소유 세션을 만든다. */
    public String start(Long userId) {
        return store.start(userId);
    }

    /** 해당 사용자·대화 쌍만 지워 늦은 AI 응답의 저장을 막는다. */
    public void close(Long userId, String conversationId) {
        store.close(userId, conversationId);
    }
}
