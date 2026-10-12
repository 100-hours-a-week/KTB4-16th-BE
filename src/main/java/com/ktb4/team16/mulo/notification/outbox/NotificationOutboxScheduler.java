package com.ktb4.team16.mulo.notification.outbox;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** DB에 남은 즉시 처리 또는 재시도 예정 Outbox 이벤트를 주기적으로 회수한다. */
@Component
public class NotificationOutboxScheduler {

    private static final int BATCH_SIZE = 20;

    private final NotificationOutboxRepository outbox;
    private final NotificationOutboxProcessor processor;
    private final Clock clock;

    /** 서버당 동기 worker 하나와 저장소를 연결한다. */
    public NotificationOutboxScheduler(NotificationOutboxRepository outbox,
            NotificationOutboxProcessor processor, Clock clock) {
        this.outbox = outbox;
        this.processor = processor;
        this.clock = clock;
    }

    /** 1분마다 최대 20개 후보를 골라 이벤트마다 별도 트랜잭션으로 처리한다. */
    @Scheduled(fixedDelay = 60_000)
    public void processDueEvents() {
        outbox.findDueEventIds(LocalDateTime.now(clock), BATCH_SIZE)
                .forEach(processor::process);
    }
}
