package com.ktb4.team16.mulo.friend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxProcessor;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxScheduler;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 일회용 DB에서 실제 친구 명령과 Outbox의 잠금 순환을 단계별로 재현한다. */
@SpringBootTest
class FriendDeadlockMysqlIntegrationTest {

    @Autowired
    private FriendCommandTransactionalService commands;
    @Autowired
    private NotificationOutboxProcessor processor;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // 예약 실행만 막는다. 명령·이벤트 처리·Repository는 실제 빈으로 실행한다.
    @MockitoBean
    private NotificationOutboxScheduler scheduler;

    private final List<Long> userIds = new ArrayList<>();

    /** Flyway가 시작되기 전에 로컬 일회용 DB만 데이터소스로 허용한다. */
    @DynamicPropertySource
    static void isolatedDatasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("DB_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/mulo_friend_deadlock_it\\?.*")) {
            throw new IllegalStateException("DB_URL은 로컬 mulo_friend_deadlock_it 전용이어야 한다");
        }
        if (!url.equals(System.getenv("MULO_DEADLOCK_IT_ADMIN_URL"))) {
            throw new IllegalStateException("관찰 연결과 앱 연결의 DB URL이 같아야 한다");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    /** 이 테스트가 만든 행만 FK 순서에 따라 정리한다. */
    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update("""
                    DELETE FROM outbox_events
                    WHERE event_type IN ('FRIEND_REQUEST', 'FRIEND_ACCEPTED')
                      AND JSON_CONTAINS(payload, JSON_ARRAY(?), '$.recipientUserIds')
                    """, userId);
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM friend_requests WHERE requester_id = ? OR addressee_id = ?",
                    userId, userId);
            jdbc.update("DELETE FROM friendships WHERE user_low_id = ? OR user_high_id = ?",
                    userId, userId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    /** 사용자 잠금과 요청 알림이 겹쳐도 역방향 요청 및 두 이벤트가 완료되어야 한다. */
    @Test
    void oppositeRequestAndNotificationCompleteWithoutLockCycle() throws Exception {
        assertIsolatedDatabase();
        TestUser first = insertUser("dlA");
        TestUser second = insertUser("dlB");
        var initial = commands.sendFriendRequest(first.id(), second.nickname());
        UUID requestEventId = initial.eventIds().getFirst();
        long requestId = ((SendFriendRequestResult.Pending) initial.result()).friendRequestId();
        CountDownLatch continueRequest = new CountDownLatch(1);
        CompletableFuture<Long> commandConnection = new CompletableFuture<>();
        var executor = Executors.newFixedThreadPool(2);

        try (Connection observer = openObserver()) {
            var requestFuture = executor.submit(() -> {
                try {
                    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    return transaction.execute(status -> {
                        jdbc.queryForList("""
                                SELECT user_id FROM users WHERE user_id IN (?, ?)
                                ORDER BY user_id FOR UPDATE
                                """, first.id(), second.id());
                        commandConnection.complete(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        awaitSignal(continueRequest);
                        // 같은 트랜잭션에서 호출해 관찰한 사용자 X 잠금을 그대로 유지한다.
                        return commands.sendFriendRequest(second.id(), first.nickname());
                    });
                } catch (RuntimeException failure) {
                    commandConnection.completeExceptionally(failure);
                    throw failure;
                }
            });
            long blockingConnectionId = commandConnection.get(15, TimeUnit.SECONDS);
            var notificationFuture = executor.submit(() -> processor.process(requestEventId));
            LockObservation locks = awaitUserLockWait(observer, blockingConnectionId, requestId);
            System.out.println("DEADLOCK_LOCK_OBSERVATION " + locks);
            continueRequest.countDown();

            Object requestOutcome;
            try {
                var accepted = requestFuture.get(15, TimeUnit.SECONDS);
                requestOutcome = accepted.result();
                accepted.eventIds().forEach(processor::process);
            } catch (java.util.concurrent.ExecutionException failure) {
                requestOutcome = failure.getCause();
            }
            notificationFuture.get(15, TimeUnit.SECONDS);
            List<Map<String, Object>> events = jdbc.queryForList("""
                    SELECT event_type, status, retry_count, last_error_code
                    FROM outbox_events
                    WHERE JSON_CONTAINS(payload, JSON_ARRAY(?), '$.recipientUserIds')
                       OR JSON_CONTAINS(payload, JSON_ARRAY(?), '$.recipientUserIds')
                    ORDER BY event_type
                    """, first.id(), second.id());
            System.out.println("DEADLOCK_REQUEST_OUTCOME " + requestOutcome);
            System.out.println("DEADLOCK_OUTBOX_OUTCOMES " + events);
            Map<String, Object> state = jdbc.queryForMap("""
                    SELECT
                      (SELECT COUNT(*) FROM friend_requests WHERE friend_request_id = ?) AS pending_count,
                      (SELECT COUNT(*) FROM friendships WHERE user_low_id = ? AND user_high_id = ?)
                        AS friendship_count,
                      (SELECT COUNT(*) FROM notifications WHERE type = 'FRIEND_ACCEPTED'
                        AND user_id IN (?, ?)) AS accepted_notification_count,
                      (SELECT COUNT(*) FROM notifications WHERE type = 'FRIEND_REQUEST'
                        AND user_id = ? AND friend_request_id IS NULL) AS late_request_notification_count
                    """, requestId, Math.min(first.id(), second.id()), Math.max(first.id(), second.id()),
                    first.id(), second.id(), second.id());
            System.out.println("DEADLOCK_FINAL_STATE " + state);
            printDeadlock(observer);

            // process()는 실패를 기록하고 반환하므로 예외뿐 아니라 잠금과 영속 상태도 검사한다.
            assertThat(locks.requestSharedLockHeld())
                    .as("알림이 사용자 잠금을 기다리는 동안 요청 S 잠금을 보유하면 잠금 순환이 생긴다")
                    .isFalse();
            assertThat(requestOutcome).isInstanceOf(SendFriendRequestResult.BecameFriends.class);
            assertThat(((Number) state.get("pending_count")).longValue()).isZero();
            assertThat(((Number) state.get("friendship_count")).longValue()).isEqualTo(1);
            assertThat(events).hasSize(2).allSatisfy(event -> {
                assertThat(event.get("status")).isEqualTo("COMPLETED");
                assertThat(event.get("last_error_code")).isNull();
            });
            assertThat(((Number) state.get("accepted_notification_count")).longValue()).isEqualTo(2);
            assertThat(((Number) state.get("late_request_notification_count")).longValue()).isZero();
        } finally {
            continueRequest.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** 요청 알림이 사용자 S 잠금을 먼저 얻으면 역방향 명령은 저장 완료를 기다리고 이력을 보존한다. */
    @Test
    void notificationHoldingUserLockCompletesBeforeOppositeRequest() throws Exception {
        assertIsolatedDatabase();
        TestUser first = insertUser("wfA");
        TestUser second = insertUser("wfB");
        var initial = commands.sendFriendRequest(first.id(), second.nickname());
        UUID requestEventId = initial.eventIds().getFirst();
        long requestId = ((SendFriendRequestResult.Pending) initial.result()).friendRequestId();
        var executor = Executors.newFixedThreadPool(2);

        try (Connection observer = openObserver()) {
            observer.setAutoCommit(false);
            try {
                holdRequestBarrier(observer, requestId);
                var notificationFuture = executor.submit(() -> processor.process(requestEventId));
                RequestLockObservation workerLocks = awaitRequestLockWait(observer, requestId, second.id());
                assertThat(workerLocks.recipientSharedLockHeld())
                        .as("요청 INSERT 대기 전에 수신자 사용자 S 잠금을 얻어야 한다")
                        .isTrue();

                var requestFuture = executor.submit(() ->
                        commands.sendFriendRequest(second.id(), first.nickname()));
                LockObservation commandLocks = awaitUserLockWait(observer,
                        workerLocks.workerConnectionId(), requestId);
                assertThat(commandLocks.waitingConnectionId())
                        .as("역방향 명령의 별도 연결이 worker의 사용자 S 잠금 뒤에서 대기해야 한다")
                        .isNotEqualTo(workerLocks.workerConnectionId());
                System.out.println("WORKER_FIRST_REQUEST_LOCK_OBSERVATION " + workerLocks);
                System.out.println("WORKER_FIRST_COMMAND_LOCK_OBSERVATION " + commandLocks);

                // 테스트 barrier만 해제한다. 실제 worker의 커밋이 역방향 명령을 이어 준다.
                observer.commit();
                notificationFuture.get(15, TimeUnit.SECONDS);
                var accepted = requestFuture.get(15, TimeUnit.SECONDS);
                assertThat(accepted.result()).isInstanceOf(SendFriendRequestResult.BecameFriends.class);
                accepted.eventIds().forEach(processor::process);

                Map<String, Object> state = jdbc.queryForMap("""
                        SELECT
                          (SELECT COUNT(*) FROM friend_requests WHERE friend_request_id = ?) AS pending_count,
                          (SELECT COUNT(*) FROM friendships WHERE user_low_id = ? AND user_high_id = ?)
                            AS friendship_count,
                          (SELECT COUNT(*) FROM notifications WHERE type = 'FRIEND_ACCEPTED'
                            AND user_id IN (?, ?)) AS accepted_notification_count,
                          (SELECT COUNT(*) FROM notifications WHERE type = 'FRIEND_REQUEST'
                            AND source_event_id = ? AND user_id = ?)
                            AS original_request_notification_count,
                          (SELECT COUNT(*) FROM notifications WHERE type = 'FRIEND_REQUEST'
                            AND source_event_id = ? AND friend_request_id IS NULL)
                            AS closed_request_notification_count
                        """, requestId, Math.min(first.id(), second.id()), Math.max(first.id(), second.id()),
                        first.id(), second.id(), requestEventId.toString(), second.id(),
                        requestEventId.toString());
                List<Map<String, Object>> events = jdbc.queryForList("""
                        SELECT event_type, status, retry_count, last_error_code
                        FROM outbox_events WHERE event_id IN (?, ?)
                        """, requestEventId.toString(), accepted.eventIds().getFirst().toString());
                System.out.println("WORKER_FIRST_FINAL_STATE " + state);
                System.out.println("WORKER_FIRST_OUTBOX_OUTCOMES " + events);
                assertThat(((Number) state.get("pending_count")).longValue()).isZero();
                assertThat(((Number) state.get("friendship_count")).longValue()).isEqualTo(1);
                assertThat(((Number) state.get("accepted_notification_count")).longValue()).isEqualTo(2);
                assertThat(((Number) state.get("original_request_notification_count")).longValue()).isEqualTo(1);
                assertThat(((Number) state.get("closed_request_notification_count")).longValue()).isEqualTo(1);
                assertThat(events).hasSize(2).allSatisfy(event -> {
                    assertThat(event.get("status")).isEqualTo("COMPLETED");
                    assertThat(((Number) event.get("retry_count")).longValue()).isZero();
                    assertThat(event.get("last_error_code")).isNull();
                });
            } finally {
                // 실패 경로에서도 barrier를 풀어 두 작업이 잠금 대기에 남지 않게 한다.
                observer.rollback();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** worker가 사용자 S 잠금을 얻은 뒤 요청 INSERT에서 멈추도록 테스트 전용 요청 X 잠금을 잡는다. */
    private void holdRequestBarrier(Connection observer, long requestId) throws SQLException {
        try (var statement = observer.prepareStatement("""
                SELECT friend_request_id FROM friend_requests WHERE friend_request_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, requestId);
            statement.setQueryTimeout(2);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("barrier 대상 pending 요청이 있어야 한다").isTrue();
            }
        }
    }

    /** 요청 S 잠금 대기와 같은 worker의 수신자 사용자 S 잠금 보유를 제한 시간 안에 관찰한다. */
    private RequestLockObservation awaitRequestLockWait(Connection observer, long requestId,
            long recipientId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (var statement = observer.prepareStatement("""
                    SELECT waiter.PROCESSLIST_ID,
                           EXISTS(SELECT 1 FROM performance_schema.data_locks held
                             WHERE held.ENGINE_TRANSACTION_ID = waiting.ENGINE_TRANSACTION_ID
                               AND held.OBJECT_SCHEMA = DATABASE() AND held.OBJECT_NAME = 'users'
                               AND held.INDEX_NAME = 'PRIMARY' AND held.LOCK_STATUS = 'GRANTED'
                               AND held.LOCK_TYPE = 'RECORD' AND held.LOCK_MODE LIKE 'S%'
                               AND held.LOCK_DATA = ?) AS recipient_shared
                    FROM performance_schema.data_lock_waits waits
                    JOIN performance_schema.data_locks waiting
                      ON waiting.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID
                    JOIN performance_schema.threads blocker ON blocker.THREAD_ID = waits.BLOCKING_THREAD_ID
                    JOIN performance_schema.threads waiter ON waiter.THREAD_ID = waits.REQUESTING_THREAD_ID
                    WHERE blocker.PROCESSLIST_ID = CONNECTION_ID() AND waiting.OBJECT_SCHEMA = DATABASE()
                      AND waiting.OBJECT_NAME = 'friend_requests' AND waiting.INDEX_NAME = 'PRIMARY'
                      AND waiting.LOCK_TYPE = 'RECORD' AND waiting.LOCK_STATUS = 'WAITING'
                      AND waiting.LOCK_MODE LIKE 'S%' AND waiting.LOCK_DATA = ?
                    """)) {
                statement.setString(1, Long.toString(recipientId));
                statement.setString(2, Long.toString(requestId));
                statement.setQueryTimeout(2);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return new RequestLockObservation(rows.getLong(1), rows.getBoolean(2));
                    }
                }
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("10초 내 worker의 요청 S 잠금 대기를 관찰하지 못했다");
    }

    /** 관리자 연결도 애플리케이션과 같은 일회용 스키마를 가리키는지 확인한다. */
    private Connection openObserver() throws SQLException {
        Connection connection = DriverManager.getConnection(System.getenv("MULO_DEADLOCK_IT_ADMIN_URL"),
                System.getenv("MULO_DEADLOCK_IT_ADMIN_USERNAME"),
                System.getenv("MULO_DEADLOCK_IT_ADMIN_PASSWORD"));
        if (!"mulo_friend_deadlock_it".equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalStateException("관찰 연결은 mulo_friend_deadlock_it 전용이다");
        }
        return connection;
    }

    /** 공유 개발 DB에 데이터를 만들기 전에 테스트 전용 DB 이름을 확인한다. */
    private void assertIsolatedDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("mulo_friend_deadlock_it");
    }

    /** 실제 잠금 대기가 생길 때까지 관찰하며 해당 트랜잭션의 요청 공유 잠금도 확인한다. */
    private LockObservation awaitUserLockWait(Connection observer, long blockingConnectionId,
            long requestId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (var statement = observer.prepareStatement("""
                    SELECT waiting.ENGINE_TRANSACTION_ID, waiter.PROCESSLIST_ID,
                           EXISTS(SELECT 1 FROM performance_schema.data_locks held
                             WHERE held.ENGINE_TRANSACTION_ID = waiting.ENGINE_TRANSACTION_ID
                               AND held.OBJECT_SCHEMA = DATABASE() AND held.OBJECT_NAME = 'friend_requests'
                               AND held.INDEX_NAME = 'PRIMARY' AND held.LOCK_STATUS = 'GRANTED'
                               AND held.LOCK_TYPE = 'RECORD' AND held.LOCK_MODE LIKE 'S%'
                               AND held.LOCK_DATA = ?) AS request_shared
                    FROM performance_schema.data_lock_waits waits
                    JOIN performance_schema.data_locks waiting
                      ON waiting.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID
                    JOIN performance_schema.threads blocker ON blocker.THREAD_ID = waits.BLOCKING_THREAD_ID
                    JOIN performance_schema.threads waiter ON waiter.THREAD_ID = waits.REQUESTING_THREAD_ID
                    WHERE blocker.PROCESSLIST_ID = ? AND waiting.OBJECT_SCHEMA = DATABASE()
                      AND waiting.OBJECT_NAME = 'users' AND waiting.LOCK_TYPE = 'RECORD'
                    """)) {
                statement.setString(1, Long.toString(requestId));
                statement.setLong(2, blockingConnectionId);
                statement.setQueryTimeout(2);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return new LockObservation(blockingConnectionId, rows.getLong(2),
                                rows.getString(1), rows.getBoolean(3));
                    }
                }
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("10초 내 지정 연결에 의한 사용자 잠금 대기를 관찰하지 못했다");
    }

    /** 잠금 그래프와 비교할 수 있도록 최신 InnoDB 데드락 기록을 테스트 출력에 남긴다. */
    private void printDeadlock(Connection observer) throws SQLException {
        try (var statement = observer.createStatement();
                var rows = statement.executeQuery("SHOW ENGINE INNODB STATUS")) {
            if (rows.next()) {
                String status = rows.getString("Status");
                int begin = status.indexOf("LATEST DETECTED DEADLOCK");
                int end = status.indexOf("TRANSACTIONS", Math.max(0, begin));
                if (begin >= 0) {
                    System.out.println(status.substring(begin, end > begin ? end : status.length()));
                }
            }
        }
    }

    /** 잠금 단계 동기화가 실패하면 무한 대기 대신 명확하게 테스트를 실패시킨다. */
    private void awaitSignal(CountDownLatch signal) {
        try {
            if (!signal.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("역방향 요청 진행 신호 시간 초과");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("역방향 요청 대기 중단", failure);
        }
    }

    /** 다른 테스트와 충돌하지 않는 활성 사용자 한 명을 만들고 정리 대상으로 등록한다. */
    private TestUser insertUser(String prefix) {
        String nickname = prefix + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                nickname + "@example.test", "test-hash", nickname);
        Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE nickname = ?", Long.class, nickname);
        userIds.add(id);
        return new TestUser(id, nickname);
    }

    private record TestUser(Long id, String nickname) { }

    private record LockObservation(long blockingConnectionId, long waitingConnectionId,
            String waitingTransactionId, boolean requestSharedLockHeld) { }

    private record RequestLockObservation(long workerConnectionId, boolean recipientSharedLockHeld) { }
}
