package com.ktb4.team16.mulo.notification.outbox;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxImmediateDispatcherTest {

    @Mock
    private NotificationOutboxProcessor processor;

    /** 원본 트랜잭션이 끝났으면 여러 Outbox 이벤트를 요청 경로에서 순서대로 처리한다. */
    @Test
    void processesEventsSynchronouslyAfterWorkerReturns() {
        UUID firstEventId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID secondEventId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        NotificationOutboxImmediateDispatcher dispatcher =
                new NotificationOutboxImmediateDispatcher(processor);

        dispatcher.processAfterTransaction(List.of(firstEventId, secondEventId));

        var interactions = inOrder(processor);
        interactions.verify(processor).process(firstEventId);
        interactions.verify(processor).process(secondEventId);
    }

    /** 호출자가 관리하는 transaction이 남으면 scheduler 복구에 맡기고 중복 처리를 피한다. */
    @Test
    void leavesEventsForSchedulerWhenCallerTransactionIsStillActive() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000503");
        NotificationOutboxImmediateDispatcher dispatcher =
                new NotificationOutboxImmediateDispatcher(processor);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        dispatcher.processAfterTransaction(List.of(eventId));

        verifyNoInteractions(processor);
    }

    /** 트랜잭션 상태를 다른 테스트로 누출하지 않도록 정리한다. */
    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
