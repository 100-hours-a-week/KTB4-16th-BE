package com.ktb4.team16.mulo.notification.outbox;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 커밋·커넥션 정리가 끝난 Outbox 이벤트를 요청 경로에서 동기 처리한다. */
@Service
@RequiredArgsConstructor
public class NotificationOutboxImmediateDispatcher {

    private final NotificationOutboxProcessor processor;

    /** 외부 트랜잭션이 남아 있으면 scheduler에 맡기고, 아니면 즉시 처리한다. */
    public void processAfterTransaction(List<UUID> eventIds) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        eventIds.forEach(processor::process);
    }
}
