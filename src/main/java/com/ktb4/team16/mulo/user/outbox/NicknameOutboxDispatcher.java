package com.ktb4.team16.mulo.user.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
class NicknameOutboxDispatcher {
    private final ChatNicknameOutboxRepository repository;
    private final NicknamePublisher publisher;

    /** 한 건씩 선점하고 두 큐 모두 수신한 경우에만 발행 완료로 전환한다. */
    @Scheduled(fixedDelay = 1000, scheduler = "nicknameDispatchScheduler")
    void dispatch() {
        for (int index = 0; index < 50 && !Thread.currentThread().isInterrupted(); index++) {
            var next = repository.claimNext();
            if (next.isEmpty()) {
                return;
            }
            var claim = next.get();
            var chat = publisher.send(claim, NicknameMqConfiguration.CHAT);
            var friend = publisher.send(claim, NicknameMqConfiguration.FRIEND);
            if (chat != NicknamePublisher.Outcome.CONFIRMED) {
                repository.retry(claim, "CHAT_" + chat.name());
            } else if (friend != NicknamePublisher.Outcome.CONFIRMED) {
                repository.retry(claim, "FRIEND_" + friend.name());
            } else {
                repository.published(claim);
            }
        }
    }
}
