package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.notification.event.NotificationEventPayloadValidator;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads;
import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxEventPublisherTest {

    @Mock
    private NotificationOutboxRepository outbox;

    /** Outbox INSERT는 원본 트랜잭션에 묶고 후처리 event ID만 반환한다. */
    @Test
    void returnsPublishedEventIdWithoutRegisteringInTransactionProcessing() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        var payload = new NotificationEventPayloads.FriendRequest(
                1, 301L, 10L, "요청자", List.of(20L));
        when(outbox.insertIfAbsent(
                org.mockito.ArgumentMatchers.eq(NotificationEventType.FRIEND_REQUEST),
                org.mockito.ArgumentMatchers.eq(301L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(eventId);
        NotificationOutboxEventPublisher publisher = new NotificationOutboxEventPublisher(
                outbox, new NotificationEventPayloadValidator(new ObjectMapper()), new ObjectMapper());

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        UUID publishedEventId = publisher.publish(
                NotificationEventType.FRIEND_REQUEST, 301L, payload);

        assertThat(publishedEventId).isEqualTo(eventId);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    /** 원본 트랜잭션이 없으면 독립 Outbox 행이 커밋되는 것을 막는다. */
    @Test
    void refusesToPublishWithoutAnActiveSourceTransaction() {
        var payload = new NotificationEventPayloads.FriendRequest(
                1, 301L, 10L, "요청자", List.of(20L));
        NotificationOutboxEventPublisher publisher = new NotificationOutboxEventPublisher(
                outbox, new NotificationEventPayloadValidator(new ObjectMapper()), new ObjectMapper());

        assertThatThrownBy(() -> publisher.publish(
                NotificationEventType.FRIEND_REQUEST, 301L, payload))
                .isInstanceOf(IllegalStateException.class);

        verify(outbox, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** aggregate ID 누락은 NPE 대신 계약 위반으로 처리하고 Outbox를 기록하지 않는지 검증한다. */
    @Test
    void rejectsMissingAggregateIdBeforeInsertingOutboxEvent() {
        var payload = new NotificationEventPayloads.FriendRequest(
                1, 301L, 10L, "요청자", List.of(20L));
        NotificationOutboxEventPublisher publisher = new NotificationOutboxEventPublisher(
                outbox, new NotificationEventPayloadValidator(new ObjectMapper()), new ObjectMapper());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> publisher.publish(
                NotificationEventType.FRIEND_REQUEST, null, payload))
                .isInstanceOf(IllegalArgumentException.class);

        verify(outbox, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    /** 테스트에서 설정한 Spring thread transaction 상태를 항상 제거한다. */
    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
