package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import com.ktb4.team16.mulo.match.dto.request.GroupChatJoinRequest;
import com.ktb4.team16.mulo.match.dto.response.ChatGroupJoinOrCreateResponse;
import com.ktb4.team16.mulo.match.dto.response.GroupChatJoinResponse;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Chat Server 호출은 DB snapshot 조회 트랜잭션 밖에서 멱등 재시도한다. */
@Service
@RequiredArgsConstructor
public class GroupChatEntryService {

    private static final Logger log = LoggerFactory.getLogger(GroupChatEntryService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;

    private final GroupChatEntryRequestFactory requestFactory;
    private final ChatRoomClient chatRoomClient;
    private final Clock clock;

    public GroupChatJoinResponse join(Long userId, GroupChatJoinRequest request) {
        ChatGroupJoinOrCreateRequest internalRequest = requestFactory.prepare(userId, request.voteAnswerId());
        if (!LocalDate.now(clock).equals(internalRequest.groupDate())) {
            throw new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);
        }
        ChatGroupJoinOrCreateResponse response = joinWithRetry(userId, internalRequest);
        return GroupChatJoinResponse.from(response);
    }

    private ChatGroupJoinOrCreateResponse joinWithRetry(Long userId, ChatGroupJoinOrCreateRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return chatRoomClient.joinOrCreateGroup(request);
            } catch (ChatServerClientException exception) {
                if (exception.failureType() == ChatServerClientException.FailureType.GROUP_REENTRY_FORBIDDEN) {
                    throw new MatchDomainException(ErrorCode.GROUP_CHAT_REENTRY_FORBIDDEN, exception);
                }
                if (!exception.failureType().isRetryable()) {
                    log.warn("그룹 채팅 입장 요청 거부. userId={}, attempt={}, failureType={}",
                            userId, attempt, exception.failureType());
                    throw new MatchDomainException(ErrorCode.GROUP_CHAT_UPSTREAM_REJECTED, exception);
                }
                if (attempt == MAX_ATTEMPTS) {
                    log.error("그룹 채팅 입장 재시도 한도 초과. userId={}, attempt={}, failureType={}",
                            userId, attempt, exception.failureType());
                    throw new MatchDomainException(ErrorCode.GROUP_CHAT_SERVICE_UNAVAILABLE, exception);
                }
                waitBeforeRetry(userId, attempt);
            }
        }
        throw new MatchDomainException(ErrorCode.GROUP_CHAT_SERVICE_UNAVAILABLE);
    }

    private void waitBeforeRetry(Long userId, int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("그룹 채팅 입장 재시도가 중단되었습니다. userId={}, attempt={}", userId, attempt);
            throw new MatchDomainException(ErrorCode.GROUP_CHAT_SERVICE_UNAVAILABLE, exception);
        }
    }
}
