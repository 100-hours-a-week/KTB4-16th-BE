package com.ktb4.team16.mulo.dailysong.publisher;

import com.ktb4.team16.mulo.dailysong.repository.DailySongOutboxClaimRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongOutboxClaimRepository.Claim;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
public class DailySongOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(DailySongOutboxDispatcher.class);
    private static final int BATCH_SIZE = 50;

    private final DailySongOutboxClaimRepository outbox;
    private final DailySongRabbitMqPublisher publisher;
    private final Clock clock;

    public DailySongOutboxDispatcher(DailySongOutboxClaimRepository outbox,
            DailySongRabbitMqPublisher publisher, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1_000, scheduler = "dailySongPublisherTaskScheduler")
    public void publishDueJobs() {
        for (String eventId : outbox.findDueEventIds(LocalDateTime.now(clock), BATCH_SIZE)) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            Optional<Claim> claimed = outbox.claim(eventId, now, now.plusMinutes(2));
            claimed.ifPresent(this::publish);
        }
    }

    private void publish(Claim claim) {
        DailySongRabbitMqPublisher.PublishOutcome outcome = publisher.publish(claim.jobId(), claim.eventId());
        if (outcome == DailySongRabbitMqPublisher.PublishOutcome.CONFIRMED) {
            if (outbox.markPublished(claim, LocalDateTime.now(clock)) == 0) {
                log.warn("오늘의 노래 발행 완료 상태를 기록하지 못했습니다: eventId={}", claim.eventId());
            }
            return;
        }

        long delaySeconds = Math.min(300L, 5L << Math.min(claim.attemptCount() - 1, 6));
        LocalDateTime nextPublishAt = LocalDateTime.now(clock).plusSeconds(delaySeconds);
        if (outbox.scheduleRetry(claim, nextPublishAt, outcome.name()) == 0) {
            log.warn("오늘의 노래 발행 재시도 상태를 기록하지 못했습니다: eventId={}", claim.eventId());
        }
    }
}
