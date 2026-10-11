package com.ktb4.team16.mulo.notification.outbox;

import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 이벤트별 짧은 트랜잭션에서 전체 알림 생성과 Outbox 상태를 함께 처리한다. */
@Service
public class NotificationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxProcessor.class);
    private static final int SCHEDULER_BATCH_SIZE = 20;

    private final NotificationOutboxRepository outbox;
    private final NotificationEventHandler handler;
    private final NotificationRetryPolicy retryPolicy;
    private final Clock clock;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    /** 원본 작업과 분리된 새 트랜잭션에서 JDBC savepoint를 사용하도록 준비한다. */
    public NotificationOutboxProcessor(NotificationOutboxRepository outbox,
            NotificationEventHandler handler, Clock clock, DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.handler = handler;
        this.clock = clock;
        this.dataSource = dataSource;
        this.retryPolicy = new NotificationRetryPolicy();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 지정 이벤트를 독립 트랜잭션으로 처리하고 실패해도 원본 비즈니스 성공을 보존한다. */
    public void process(UUID eventId) {
        try {
            transactionTemplate.executeWithoutResult(status -> processLocked(eventId));
        } catch (RuntimeException exception) {
            log.warn("Outbox 이벤트 트랜잭션 롤백: eventId={}, failureType={}",
                    eventId, exception.getClass().getSimpleName());
            recordFailureAfterRollback(eventId, exception);
        }
    }

    /** due 상태를 잠금 재확인하고 Savepoint로 전체 알림 집합을 원자적으로 처리한다. */
    private void processLocked(UUID eventId) {
        LocalDateTime now = LocalDateTime.now(clock);
        var event = outbox.lockDueEvent(eventId, now);
        if (event.isEmpty()) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        Savepoint savepoint = createSavepoint(connection);
        try {
            for (NotificationDraft draft : handler.handle(event.get())) {
                outbox.insertNotification(eventId, draft);
            }
            outbox.markCompleted(eventId, LocalDateTime.now(clock));
            releaseSavepoint(connection, savepoint);
        } catch (RuntimeException exception) {
            rollbackSavepoint(connection, savepoint);
            if (exception instanceof NotificationEventPermanentException
                    || exception instanceof com.ktb4.team16.mulo.notification.event
                            .InvalidNotificationEventPayloadException) {
                markPermanentFailure(event.get(), errorCode(exception));
            } else {
                recordRetry(event.get(), errorCode(exception));
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /** Spring 트랜잭션에 결합된 JDBC 연결에서 이벤트별 Savepoint를 생성한다. */
    private Savepoint createSavepoint(Connection connection) {
        try {
            return connection.setSavepoint();
        } catch (SQLException exception) {
            throw new IllegalStateException("OUTBOX_SAVEPOINT_UNAVAILABLE", exception);
        }
    }

    /** 알림 일부 INSERT를 취소하면서 바깥 이벤트 잠금은 유지한다. */
    private void rollbackSavepoint(Connection connection, Savepoint savepoint) {
        try {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        } catch (SQLException exception) {
            throw new IllegalStateException("OUTBOX_SAVEPOINT_ROLLBACK_FAILED", exception);
        }
    }

    /** 성공한 이벤트 트랜잭션을 마무리하기 전에 Savepoint 자원을 해제한다. */
    private void releaseSavepoint(Connection connection, Savepoint savepoint) {
        try {
            connection.releaseSavepoint(savepoint);
        } catch (SQLException exception) {
            throw new IllegalStateException("OUTBOX_SAVEPOINT_RELEASE_FAILED", exception);
        }
    }

    /** 영구 payload 오류를 재시도 큐에 넣지 않고 FAILED로 확정한다. */
    private void markPermanentFailure(OutboxEvent event, String errorCode) {
        outbox.markFailure(event.eventId(), event.retryCount(), null, errorCode,
                LocalDateTime.now(clock), true);
        log.error("Outbox 이벤트 영구 실패: eventId={}, eventType={}, errorCode={}",
                event.eventId(), event.eventType(), errorCode);
    }

    /** 최초 실패 또는 자동 재시도 실패 횟수에 맞춰 예약 시각을 저장한다. */
    private void recordRetry(OutboxEvent event, String errorCode) {
        boolean retryAttempt = event.nextRetryAt() != null;
        int retryCount = event.retryCount() + (retryAttempt ? 1 : 0);
        LocalDateTime now = LocalDateTime.now(clock);
        if (retryAttempt && retryCount >= NotificationRetryPolicy.MAX_ADDITIONAL_RETRIES) {
            outbox.markFailure(event.eventId(), retryCount, null, errorCode, now, true);
            log.error("Outbox 자동 재시도 한도 초과: eventId={}, eventType={}, errorCode={}",
                    event.eventId(), event.eventType(), errorCode);
            return;
        }
        int jitterSeconds = ThreadLocalRandom.current()
                .nextInt(NotificationRetryPolicy.MAX_JITTER_SECONDS + 1);
        LocalDateTime nextRetryAt = now.plus(retryPolicy.delayForFailure(retryCount, jitterSeconds));
        outbox.markFailure(event.eventId(), retryCount, nextRetryAt, errorCode, now, false);
        log.warn("Outbox 이벤트 재시도 예약: eventId={}, eventType={}, retryCount={}, nextRetryAt={}, errorCode={}",
                event.eventId(), event.eventType(), retryCount, nextRetryAt, errorCode);
    }

    /** rollback 뒤에도 DB가 응답하면 행 잠금을 다시 얻어 실패 예약을 안전하게 기록한다. */
    private void recordFailureAfterRollback(UUID eventId, RuntimeException exception) {
        try {
            transactionTemplate.executeWithoutResult(status -> outbox.lockDueEvent(
                    eventId, LocalDateTime.now(clock)).ifPresent(event -> {
                        if (exception instanceof NotificationEventPermanentException
                                || exception instanceof com.ktb4.team16.mulo.notification.event
                                        .InvalidNotificationEventPayloadException) {
                            markPermanentFailure(event, errorCode(exception));
                        } else {
                            recordRetry(event, errorCode(exception));
                        }
                    }));
        } catch (RuntimeException recordException) {
            log.error("Outbox 실패 상태를 기록하지 못함: eventId={}, failureType={}",
                    eventId, recordException.getClass().getSimpleName());
        }
    }

    /** 민감한 이벤트 payload 대신 고정 오류 코드만 저장하고 기록한다. */
    private String errorCode(RuntimeException exception) {
        String code = exception instanceof NotificationEventPermanentException permanent
                ? permanent.errorCode()
                : exception instanceof com.ktb4.team16.mulo.notification.event
                        .InvalidNotificationEventPayloadException
                        ? exception.getMessage()
                        : exception.getClass().getSimpleName();
        return code == null ? "UNKNOWN_NOTIFICATION_ERROR" : code.substring(0, Math.min(code.length(), 100));
    }
}
