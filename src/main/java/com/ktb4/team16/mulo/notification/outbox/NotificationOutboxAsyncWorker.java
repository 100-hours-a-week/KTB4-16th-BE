package com.ktb4.team16.mulo.notification.outbox;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationOutboxAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxAsyncWorker.class);

    private final NotificationOutboxProcessor processor;

    @Async("notificationOutboxExecutor")
    public void process(List<UUID> eventIds) {
        for (UUID eventId : eventIds) {
            try {
                NotificationOutboxProcessor.ProcessOutcome outcome = processor.processWithOutcome(eventId);
                switch (outcome) {
                    case COMPLETED -> log.debug("Outbox 비동기 이벤트 처리 완료: eventId={}", eventId);
                    case RETRY_SCHEDULED -> log.warn("Outbox 비동기 이벤트 재시도 예약: eventId={}", eventId);
                    case PERMANENT_FAILURE -> log.error("Outbox 비동기 이벤트 영구 실패: eventId={}", eventId);
                    case SKIPPED -> log.debug("Outbox 비동기 이벤트 처리 생략: eventId={}", eventId);
                    case RECOVERY_FAILED -> log.error("Outbox 비동기 이벤트 실패 상태 기록 불가: eventId={}", eventId);
                }
            } catch (RuntimeException exception) {
                log.error("Outbox 비동기 이벤트 처리 예외: eventId={}, failureType={}",
                        eventId, exception.getClass().getSimpleName());
            }
        }
    }
}
