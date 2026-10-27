package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxImmediateDispatcherTest {

    @Mock
    private NotificationOutboxAsyncWorker worker;

    /** 여러 이벤트 ID를 하나의 비동기 작업으로 제출한다. */
    @Test
    void submitsOneAsyncTaskAfterTransaction() {
        UUID firstEventId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID secondEventId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        NotificationOutboxImmediateDispatcher dispatcher = new NotificationOutboxImmediateDispatcher(worker);

        dispatcher.processAfterTransaction(List.of(firstEventId, secondEventId));

        verify(worker).process(List.of(firstEventId, secondEventId));
    }

    /** 제출 거절은 원본 요청을 실패시키지 않고 저장된 이벤트를 scheduler에 남긴다. */
    @Test
    void rejectedSubmissionDoesNotFailBusinessRequest() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000504");
        NotificationOutboxImmediateDispatcher dispatcher = new NotificationOutboxImmediateDispatcher(worker);
        doThrow(new TaskRejectedException("queue full")).when(worker).process(List.of(eventId));

        assertThatCode(() -> dispatcher.processAfterTransaction(List.of(eventId))).doesNotThrowAnyException();

        verify(worker).process(List.of(eventId));
    }

    /** 제출 거절 이외의 오류는 무조건 숨기지 않는다. */
    @Test
    void unrelatedProgrammingErrorIsNotSuppressed() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000505");
        NotificationOutboxImmediateDispatcher dispatcher = new NotificationOutboxImmediateDispatcher(worker);
        doThrow(new IllegalStateException("unexpected")).when(worker).process(List.of(eventId));

        assertThatThrownBy(() -> dispatcher.processAfterTransaction(List.of(eventId)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 호출자가 관리하는 transaction이 남으면 scheduler 복구에 맡긴다. */
    @Test
    void leavesEventsForSchedulerWhenCallerTransactionIsStillActive() {
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000503");
        NotificationOutboxImmediateDispatcher dispatcher = new NotificationOutboxImmediateDispatcher(worker);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        dispatcher.processAfterTransaction(List.of(eventId));

        verifyNoInteractions(worker);
    }

    /** 이벤트가 없으면 Executor 큐를 차지하지 않는다. */
    @Test
    void doesNotSubmitEmptyBatch() {
        NotificationOutboxImmediateDispatcher dispatcher = new NotificationOutboxImmediateDispatcher(worker);

        dispatcher.processAfterTransaction(List.of());

        verifyNoInteractions(worker);
    }

    /** 트랜잭션 상태를 다른 테스트로 누출하지 않도록 정리한다. */
    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
