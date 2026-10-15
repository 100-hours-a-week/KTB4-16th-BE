package com.ktb4.team16.mulo.memorysearch.ai;

import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import java.util.List;

/** 실제 AI 연결과 로컬 임시 결과가 공유하는 검색 경계다. */
public interface MemorySearchAiGateway {

    /** 인증 사용자 질문과 완료된 최근 문맥으로 후보 ID와 답변을 생성한다. */
    MemorySearchAiResult search(Long userId, String query, List<MemorySearchTurn> history);
}
