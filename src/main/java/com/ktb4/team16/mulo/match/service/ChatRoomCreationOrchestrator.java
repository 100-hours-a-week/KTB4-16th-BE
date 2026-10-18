package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.ChatRoomCreateResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** HTTP 호출에는 트랜잭션을 열지 않고, 재시도 전체에서 동일 요청 객체를 재사용한다. */
@Service
@RequiredArgsConstructor
public class ChatRoomCreationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomCreationOrchestrator.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;

    private final ChatRoomClient chatRoomClient;
    private final MatchCompletionService matchCompletionService;

    public void createAfterMatch(Long matchId, ChatRoomCreateRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ChatRoomCreateResponse response;
            try {
                response = chatRoomClient.createChatRoom(request);
            } catch (ChatServerClientException exception) {
                if (!exception.failureType().isRetryable() || attempt == MAX_ATTEMPTS) {
                    logFailure(matchId, exception.failureType(), attempt);
                    fail(matchId);
                    return;
                }
                if (!waitBeforeRetry(matchId, attempt)) {
                    return;
                }
                continue;
            }
            try {
                matchCompletionService.complete(matchId, response.chatRoomId());
            } catch (RuntimeException exception) {
                // Chat Server는 이미 방을 만들었을 수 있으므로 FAILED로 덮어쓰지 않는다.
                log.error("채팅방 생성 결과 저장 실패: matchId={}, failureType={}", matchId,
                        exception.getClass().getSimpleName());
            }
            return;
        }
    }

    private boolean waitBeforeRetry(Long matchId, int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("채팅방 생성 재시도가 중단됨: matchId={}, attempt={}", matchId, attempt);
            fail(matchId);
            return false;
        }
    }

    private void fail(Long matchId) {
        try {
            matchCompletionService.fail(matchId);
        } catch (RuntimeException exception) {
            log.error("매칭 실패 상태 저장 실패: matchId={}, failureType={}", matchId,
                    exception.getClass().getSimpleName());
        }
    }

    private void logFailure(Long matchId, ChatServerClientException.FailureType failureType, int attempt) {
        log.warn("채팅방 생성 최종 실패: matchId={}, attempt={}, failureType={}", matchId, attempt, failureType);
    }
}
