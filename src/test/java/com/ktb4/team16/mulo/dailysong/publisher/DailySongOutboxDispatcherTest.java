package com.ktb4.team16.mulo.dailysong.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.repository.DailySongOutboxClaimRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongOutboxClaimRepository.Claim;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailySongOutboxDispatcherTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final Claim CLAIM = new Claim("event-42", 42L, 1, "token");

    private final DailySongOutboxClaimRepository outbox = mock(DailySongOutboxClaimRepository.class);
    private final DailySongRabbitMqPublisher publisher = mock(DailySongRabbitMqPublisher.class);
    private final DailySongOutboxDispatcher dispatcher = new DailySongOutboxDispatcher(outbox, publisher, CLOCK);

    @Test
    void confirmedPublishMarksOnlyClaimedEventPublished() {
        when(outbox.findDueEventIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of("event-42"));
        when(outbox.claim(eq("event-42"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(CLAIM));
        when(publisher.publish(42L, "event-42"))
                .thenReturn(DailySongRabbitMqPublisher.PublishOutcome.CONFIRMED);
        when(outbox.markPublished(eq(CLAIM), any(LocalDateTime.class))).thenReturn(1);

        dispatcher.publishDueJobs();

        verify(outbox).markPublished(CLAIM, LocalDateTime.now(CLOCK));
    }

    @Test
    void failedPublishSchedulesRetryAndKeepsEvent() {
        when(outbox.findDueEventIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of("event-42"));
        when(outbox.claim(eq("event-42"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(CLAIM));
        when(publisher.publish(42L, "event-42"))
                .thenReturn(DailySongRabbitMqPublisher.PublishOutcome.NACKED);
        when(outbox.scheduleRetry(eq(CLAIM), any(LocalDateTime.class), eq("NACKED"))).thenReturn(1);

        dispatcher.publishDueJobs();

        verify(outbox).scheduleRetry(CLAIM, LocalDateTime.now(CLOCK).plusSeconds(5), "NACKED");
    }

    @Test
    void staleClaimAfterDuplicatePublishCannotOverwriteNewOwner() {
        when(outbox.findDueEventIds(any(LocalDateTime.class), eq(50))).thenReturn(List.of("event-42"));
        when(outbox.claim(eq("event-42"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(CLAIM));
        when(publisher.publish(42L, "event-42"))
                .thenReturn(DailySongRabbitMqPublisher.PublishOutcome.CONFIRMED);
        when(outbox.markPublished(eq(CLAIM), any(LocalDateTime.class))).thenReturn(0);

        dispatcher.publishDueJobs();

        verify(outbox).markPublished(CLAIM, LocalDateTime.now(CLOCK));
    }
}
