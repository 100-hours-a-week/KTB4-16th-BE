package com.ktb4.team16.mulo.user.event;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.match.client.ChatServerClientException;
import com.ktb4.team16.mulo.user.service.UserProfileService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatNicknameSyncAfterCommitListener {
    private static final Logger log = LoggerFactory.getLogger(ChatNicknameSyncAfterCommitListener.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final Object[] USER_LOCKS = createUserLocks();

    private final UserProfileService userProfileService;
    private final ChatRoomClient chatRoomClient;

    /** 같은 인스턴스에서 같은 사용자의 동기화 순서를 직렬화하고 커밋 후에만 실행한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserNicknameChangedEvent event) {
        synchronized (lockFor(event.userId())) {
            synchronizeLatestNickname(event.userId());
        }
    }

    private void synchronizeLatestNickname(Long userId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String latestNickname = userProfileService.getCurrentNicknameForChatSync(userId);
                chatRoomClient.updateParticipantNickname(userId, latestNickname);
                return;
            } catch (ChatServerClientException exception) {
                if (!exception.failureType().isRetryable() || attempt == MAX_ATTEMPTS) {
                    log.warn("Chat Server 닉네임 동기화 실패: userId={}, attempt={}, failureType={}, httpStatus={}",
                            userId, attempt, exception.failureType(), exception.httpStatus());
                    return;
                }
                if (!waitBeforeRetry(userId, attempt)) {
                    return;
                }
            } catch (RuntimeException exception) {
                log.error("커밋 후 Chat Server 닉네임 동기화 리스너 오류: userId={}, failureType={}",
                        userId, exception.getClass().getSimpleName());
                return;
            }
        }
    }

    private boolean waitBeforeRetry(Long userId, int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Chat Server 닉네임 동기화 재시도 중단: userId={}, attempt={}", userId, attempt);
            return false;
        }
    }

    private static Object lockFor(Long userId) {
        return USER_LOCKS[Math.floorMod(Long.hashCode(userId), USER_LOCKS.length)];
    }

    private static Object[] createUserLocks() {
        Object[] locks = new Object[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }
}
