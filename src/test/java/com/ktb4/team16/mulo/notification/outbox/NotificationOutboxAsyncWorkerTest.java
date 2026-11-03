package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationOutboxAsyncWorkerTest {

    /** 중간 이벤트가 예외를 던져도 나머지 이벤트를 순서대로 시도한다. */
    @Test
    void continuesSequentiallyAfterOneEventFails() {
        NotificationOutboxProcessor processor = mock(NotificationOutboxProcessor.class);
        NotificationOutboxAsyncWorker worker = new NotificationOutboxAsyncWorker(processor);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000602");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000603");
        when(processor.processWithOutcome(first)).thenReturn(NotificationOutboxProcessor.ProcessOutcome.COMPLETED);
        when(processor.processWithOutcome(second)).thenThrow(new IllegalStateException("test failure"));
        when(processor.processWithOutcome(third)).thenReturn(NotificationOutboxProcessor.ProcessOutcome.COMPLETED);

        worker.process(List.of(first, second, third));

        var order = inOrder(processor);
        order.verify(processor).processWithOutcome(first);
        order.verify(processor).processWithOutcome(second);
        order.verify(processor).processWithOutcome(third);
    }

    /** Spring 프록시를 통해 요청 스레드가 아닌 알림 전용 스레드에서 처리한다. */
    @Test
    void invokesProcessorOnNotificationExecutorThread() {
        NotificationOutboxProcessor processor = mock(NotificationOutboxProcessor.class);
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000604");
        CountDownLatch processed = new CountDownLatch(1);
        AtomicReference<Thread> processingThread = new AtomicReference<>();
        when(processor.processWithOutcome(eventId)).thenAnswer(invocation -> {
            processingThread.set(Thread.currentThread());
            processed.countDown();
            return NotificationOutboxProcessor.ProcessOutcome.COMPLETED;
        });
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(NotificationAsyncConfiguration.class, NotificationOutboxAsyncWorker.class)
                .withBean(NotificationOutboxProcessor.class, () -> processor);

        contextRunner.run(context -> {
            context.getBean(NotificationOutboxAsyncWorker.class).process(List.of(eventId));

            assertThat(processed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(processingThread.get()).isNotSameAs(Thread.currentThread());
            assertThat(processingThread.get().getName()).startsWith("notification-outbox-");
        });
    }
}
