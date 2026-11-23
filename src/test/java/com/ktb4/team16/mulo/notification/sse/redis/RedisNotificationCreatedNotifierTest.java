package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class RedisNotificationCreatedNotifierTest {

    private final NotificationSseRedisPublisher publisher = mock(NotificationSseRedisPublisher.class);
    private final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    private final NotificationSseConnectionManager connections = mock(NotificationSseConnectionManager.class);
    private final RedisNotificationCreatedNotifier notifier =
            new RedisNotificationCreatedNotifier(publisher, executor, connections);

    @Test
    void submitsPublicationWithoutSendingDirectlyToLocalConnections() {
        notifier.notificationCreated(Set.of(42L));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        verifyNoInteractions(publisher, connections);

        task.getValue().run();

        verify(publisher).publish(42L);
        verifyNoInteractions(connections);
    }

    @Test
    void failedPublicationDoesNotPreventOtherRecipientsFromBeingPublished() {
        executeImmediately();
        when(publisher.publish(42L)).thenThrow(new IllegalStateException("test publish failure"));

        notifier.notificationCreated(new LinkedHashSet<>(List.of(42L, 43L)));

        verify(publisher).publish(42L);
        verify(publisher).publish(43L);
        verify(connections).closeForResynchronization(42L);
    }

    @Test
    void rejectedSubmissionClosesAffectedConnectionsAndContinues() {
        doThrow(new RejectedExecutionException("test rejection"))
                .doAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                }).when(executor).execute(any(Runnable.class));

        notifier.notificationCreated(new LinkedHashSet<>(List.of(42L, 43L)));

        verify(connections).closeForResynchronization(42L);
        verify(publisher).publish(43L);
    }

    @Test
    void emptyRecipientsDoNotSubmitPublication() {
        notifier.notificationCreated(Set.of());

        verifyNoInteractions(executor, publisher, connections);
    }

    @Test
    void publicationRunsOnDeliveryExecutorThread() throws Exception {
        ThreadPoolTaskExecutor actualExecutor = new ThreadPoolTaskExecutor();
        actualExecutor.setCorePoolSize(1);
        actualExecutor.setMaxPoolSize(1);
        actualExecutor.setThreadNamePrefix("notification-sse-delivery-test-");
        actualExecutor.initialize();
        var thread = new CompletableFuture<Thread>();
        when(publisher.publish(42L)).thenAnswer(invocation -> {
            thread.complete(Thread.currentThread());
            return 1L;
        });
        try {
            new RedisNotificationCreatedNotifier(publisher, actualExecutor, connections)
                    .notificationCreated(Set.of(42L));

            Thread publicationThread = thread.get(2, TimeUnit.SECONDS);
            assertThat(publicationThread).isNotSameAs(Thread.currentThread());
            assertThat(publicationThread.getName()).startsWith("notification-sse-delivery-test-");
        } finally {
            actualExecutor.shutdown();
        }
    }

    private void executeImmediately() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
    }
}
