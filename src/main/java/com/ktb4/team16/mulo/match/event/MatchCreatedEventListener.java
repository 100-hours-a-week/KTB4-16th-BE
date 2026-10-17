package com.ktb4.team16.mulo.match.event;

import com.ktb4.team16.mulo.match.service.ChatRoomCreationOrchestrator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MatchCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(MatchCreatedEventListener.class);

    private final ChatRoomCreationOrchestrator orchestrator;

    /** 실패를 삼켜 이미 commit된 매칭 생성 API의 응답을 외부 장애로 바꾸지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchCreatedEvent event) {
        try {
            orchestrator.createAfterMatch(event.matchId(), event.chatRoomRequest());
        } catch (RuntimeException exception) {
            log.error("커밋 후 채팅방 생성 리스너 오류: matchId={}, failureType={}", event.matchId(),
                    exception.getClass().getSimpleName());
        }
    }
}
