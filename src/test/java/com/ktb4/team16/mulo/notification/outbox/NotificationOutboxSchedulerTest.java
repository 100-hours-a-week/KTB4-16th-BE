package com.ktb4.team16.mulo.notification.outbox;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxSchedulerTest {

    @Mock
    private NotificationOutboxRepository outbox;

    @Mock
    private NotificationOutboxProcessor processor;

    /** 기존 polling 경로는 due 이벤트를 최대 20개 읽고 기존 Processor로 넘긴다. */
    @Test
    void processesDueEventsWithExistingProcessor() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000702");
        when(outbox.findDueEventIds(LocalDateTime.now(clock), 20)).thenReturn(List.of(first, second));

        new NotificationOutboxScheduler(outbox, processor, clock).processDueEvents();

        var order = inOrder(outbox, processor);
        order.verify(outbox).findDueEventIds(LocalDateTime.now(clock), 20);
        order.verify(processor).process(first);
        order.verify(processor).process(second);
    }
}
