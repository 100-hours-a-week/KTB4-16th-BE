package com.ktb4.team16.mulo.user.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
class NicknameOutboxDispatcher {
    private final ChatNicknameOutboxRepository repository;
    private final NicknamePublisher publisher;
    private final boolean friendsEnabled;

    /** 한 건씩 선점하고 활성 목적지 모두 발행을 확인한 경우에만 발행 완료로 전환한다. */
    @Scheduled(fixedDelay = 1000, scheduler = "nicknameDispatchScheduler")
    void dispatch() {
        for (int index = 0; index < 50 && !Thread.currentThread().isInterrupted(); index++) {
            var next = repository.claimNext();
            if (next.isEmpty()) {
                return;
            }
            var claim = next.get();
            var chat = publisher.send(claim, NicknameMqConfiguration.CHAT);
            var friend = friendsEnabled ? publisher.send(claim, NicknameMqConfiguration.FRIEND) : null;
            if (chat != NicknamePublisher.Outcome.CONFIRMED) {
                repository.retry(claim, "CHAT_" + chat.name());
            } else if (friendsEnabled && friend != NicknamePublisher.Outcome.CONFIRMED) {
                repository.retry(claim, "FRIEND_" + friend.name());
            } else {
                repository.published(claim);
            }
        }
    }
}
