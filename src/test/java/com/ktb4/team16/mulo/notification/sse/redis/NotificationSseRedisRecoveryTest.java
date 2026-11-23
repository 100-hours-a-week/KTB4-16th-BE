package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.notification.sse.LocalNotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationSseRedisRecoveryTest {

    private final NotificationSseRedisTransport transport = mock(NotificationSseRedisTransport.class);
    private final LocalNotificationCreatedNotifier localNotifier = mock(LocalNotificationCreatedNotifier.class);
    private final NotificationSseConnectionManager connections = mock(NotificationSseConnectionManager.class);
    private final ScheduledExecutorService monitor = mock(ScheduledExecutorService.class);
    private final AtomicBoolean healthy = new AtomicBoolean();
    private final NotificationSseRedisSubscriber subscriber = new NotificationSseRedisSubscriber(
            transport, localNotifier, connections, Duration.ofSeconds(1), monitor);

    @BeforeEach
    void setUp() {
        when(transport.isSubscriptionHealthy()).thenAnswer(invocation -> healthy.get());
        doAnswer(invocation -> {
            healthy.set(true);
            return null;
        }).when(transport).subscribe(any());
    }

    @AfterEach
    void close() {
        subscriber.close();
    }

    @Test
    void initialSubscriptionFailureIsRetriedOnNextCheck() {
        doThrow(new IllegalStateException("test connection failure"))
                .doAnswer(invocation -> {
                    healthy.set(true);
                    return null;
                }).when(transport).subscribe(any());

        subscriber.start();

        verify(connections, never()).setDeliveryAvailable(true);
        nextCheck().run();

        verify(transport, times(2)).subscribe(any());
        verify(connections).setDeliveryAvailable(true);
    }

    @Test
    void lostSubscriptionClosesStreamsBeforeResubscribing() {
        subscriber.start();
        Runnable check = nextCheck();
        clearInvocations(connections, transport);
        healthy.set(false);

        check.run();

        var order = inOrder(connections, transport);
        order.verify(connections).setDeliveryAvailable(false);
        order.verify(transport).unsubscribe();
        order.verify(transport).subscribe(any());
        order.verify(connections).setDeliveryAvailable(true);
    }

    @Test
    void failedSubscriptionHealthCheckKeepsDeliveryUnavailable() {
        doAnswer(invocation -> null).when(transport).subscribe(any());

        subscriber.start();
        nextCheck().run();

        verify(connections, never()).setDeliveryAvailable(true);
    }

    @Test
    void failedCleanupDoesNotStopSubsequentRecoveryChecks() {
        subscriber.start();
        Runnable check = nextCheck();
        clearInvocations(connections, transport);
        healthy.set(false);
        doThrow(new IllegalStateException("test cleanup failure")).doNothing().when(transport).unsubscribe();

        check.run();

        verify(transport, never()).subscribe(any());
        verify(connections, never()).setDeliveryAvailable(true);

        check.run();

        verify(transport).subscribe(any());
        verify(connections).setDeliveryAvailable(true);
    }

    @Test
    void healthyChecksKeepExistingStreamsOpen() {
        subscriber.start();
        Runnable check = nextCheck();
        clearInvocations(connections, transport);

        check.run();

        verify(transport).isSubscriptionHealthy();
        verifyNoInteractions(connections);
        verify(transport, never()).subscribe(any());
    }

    @Test
    void closedSubscriberCannotRetryOrReenableDelivery() {
        subscriber.start();
        Runnable check = nextCheck();
        subscriber.close();
        clearInvocations(transport, connections);

        check.run();

        verifyNoInteractions(transport, connections);
        verify(monitor).shutdownNow();
        assertThatThrownBy(subscriber::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidCheckIntervalIsRejected() {
        assertThatThrownBy(() -> new NotificationSseRedisSubscriber(
                transport, localNotifier, connections, Duration.ZERO, monitor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Runnable nextCheck() {
        ArgumentCaptor<Runnable> check = ArgumentCaptor.forClass(Runnable.class);
        verify(monitor).scheduleWithFixedDelay(check.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        return check.getValue();
    }
}
