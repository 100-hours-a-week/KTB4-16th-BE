package com.ktb4.team16.mulo.memorysearch.ai;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 실제 AI 명세 수신 전에는 로컬 외 환경에서 임시 결과 공개를 막는다. */
@Component
@Profile("!local")
public class UnavailableMemorySearchAiGateway implements MemorySearchAiGateway {

    /** 로컬 이외 환경의 질문은 정해진 공개 AI 장애 코드로 거부한다. */
    @Override
    public MemorySearchAiResult search(Long userId, String query,
            List<MemorySearchTurn> history) {
        throw new MemorySearchDomainException(ErrorCode.AI_SERVICE_ERROR);
    }
}
