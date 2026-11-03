package com.ktb4.team16.mulo.notification.outbox;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NotificationOutboxImmediateDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxImmediateDispatcher.class);

    private final NotificationOutboxAsyncWorker worker;

    public void processAfterTransaction(List<UUID> eventIds) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        if (eventIds.isEmpty()) {
            return;
        }
        List<UUID> submittedIds = List.copyOf(eventIds);
        try {
            worker.process(submittedIds);
            log.debug("Outbox 비동기 작업 제출: eventIds={}", submittedIds);
        } catch (TaskRejectedException exception) {
            log.warn("Outbox 비동기 작업 제출 거절: eventIds={}, failureType={}",
                    submittedIds, exception.getClass().getSimpleName());
        }
    }
}
