package com.ktb4.team16.mulo.memorysearch.session;

import java.util.List;
import java.util.Optional;

/** 대화 식별과 V3 문맥용 최근 턴의 저장 경계다. */
public interface MemorySearchConversationStore {

    /** 인증 사용자의 새 대화 ID를 발급하고 유휴 만료를 설정한다. */
    String start(Long userId);

    /** 해당 사용자의 대화가 활성 상태인지 확인한다. */
    boolean isActive(Long userId, String conversationId);

    /** 활성 대화의 최근 턴을 오래된 순으로 조회하며 종료·만료 시 빈 값을 반환한다. */
    Optional<List<MemorySearchTurn>> read(Long userId, String conversationId);

    /** 활성 대화에만 완료된 턴을 추가하고 유휴 만료를 갱신한다. */
    boolean appendIfActive(Long userId, String conversationId, MemorySearchTurn turn);

    /** 지정한 사용자의 대화 ID와 최근 턴을 함께 제거한다. */
    void close(Long userId, String conversationId);
}
