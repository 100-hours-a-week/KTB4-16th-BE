package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.repository.DailySongNotificationRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongNotificationRepository.NotificationCandidate;
import com.ktb4.team16.mulo.dailysong.service.DailySongNotificationBatchService;
import com.ktb4.team16.mulo.notification.entity.NotificationType;
import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import com.ktb4.team16.mulo.notification.outbox.NotificationDraft;
import com.ktb4.team16.mulo.notification.outbox.NotificationEventHandler;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxProcessor;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxRepository;
import com.ktb4.team16.mulo.notification.outbox.OutboxEvent;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import java.sql.Connection;
import java.sql.Savepoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationSseRedisCommitTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final NotificationSseRedisPublisher publisher = mock(NotificationSseRedisPublisher.class);
    private final ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    private final NotificationSseConnectionManager connections = mock(NotificationSseConnectionManager.class);
    private final RedisNotificationCreatedNotifier notifier =
            new RedisNotificationCreatedNotifier(publisher, executor, connections);
    private final DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T08:00:00Z"), ZoneOffset.UTC);
    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final NotificationEventHandler handler = mock(NotificationEventHandler.class);
    private final UUID eventId = UUID.randomUUID();
    private final OutboxEvent event = new OutboxEvent(eventId,
            NotificationEventType.MONTHLY_REPORT_CREATED, 1L, "{}", 0, null);
    private final NotificationDraft draft = new NotificationDraft(42L,
            NotificationType.MONTHLY_REPORT_CREATED, 1L, "title", "content");

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.setSavepoint()).thenReturn(mock(Savepoint.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
    }

    @Test
    void outboxPublishesOnlyAfterNotificationCommit() throws Exception {
        when(outbox.lockDueEvent(eq(eventId), any(LocalDateTime.class))).thenReturn(Optional.of(event));
        when(handler.handle(event)).thenReturn(List.of(draft));
        when(outbox.insertNotification(eventId, draft)).thenAnswer(invocation -> {
            verifyNoInteractions(publisher);
            return 1;
        });

        processor().process(eventId);

        var order = inOrder(outbox, connection, publisher);
        order.verify(outbox).markCompleted(eq(eventId), any(LocalDateTime.class));
        order.verify(connection).commit();
        order.verify(publisher).publish(42L);
    }

    @Test
    void outboxDoesNotPublishWhenNotificationInsertFails() throws Exception {
        when(outbox.lockDueEvent(eq(eventId), any(LocalDateTime.class))).thenReturn(Optional.of(event));
        when(handler.handle(event)).thenReturn(List.of(draft));
        when(outbox.insertNotification(eventId, draft)).thenThrow(new IllegalStateException("test insert failure"));

        processor().process(eventId);

        verify(connection).rollback(any(Savepoint.class));
        verifyNoInteractions(executor, publisher);
    }

    @Test
    void outboxDoesNotPublishWhenNoNotificationWasInserted() {
        when(outbox.lockDueEvent(eq(eventId), any(LocalDateTime.class))).thenReturn(Optional.of(event));
        when(handler.handle(event)).thenReturn(List.of(draft));
        when(outbox.insertNotification(eventId, draft)).thenReturn(0);

        processor().process(eventId);

        verify(outbox).markCompleted(eq(eventId), any(LocalDateTime.class));
        verifyNoInteractions(executor, publisher);
    }

    @Test
    void dailySongPublishesOnlyAfterCommit() throws Exception {
        DailySongNotificationBatchService service = dailySongBatch();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(service.createNextBatch()).isEqualTo(1);
            verifyNoInteractions(executor, publisher);
        });

        var order = inOrder(connection, publisher);
        order.verify(connection).commit();
        order.verify(publisher).publish(42L);
    }

    @Test
    void dailySongRollbackDoesNotPublish() throws Exception {
        DailySongNotificationBatchService service = dailySongBatch();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(service.createNextBatch()).isEqualTo(1);
            status.setRollbackOnly();
        });

        verify(connection).rollback();
        verifyNoInteractions(executor, publisher);
    }

    private NotificationOutboxProcessor processor() {
        return new NotificationOutboxProcessor(outbox, handler, clock, dataSource, transactions, notifier);
    }

    private DailySongNotificationBatchService dailySongBatch() {
        DailySongNotificationRepository repository = mock(DailySongNotificationRepository.class);
        when(repository.lockDueJobs(any(), anyInt())).thenReturn(
                List.of(new NotificationCandidate(1L, 42L, eventId.toString())));
        when(repository.markNotified(eq(List.of(1L)), any(LocalDateTime.class))).thenReturn(1);
        return new DailySongNotificationBatchService(repository, notifier, clock);
    }
}
