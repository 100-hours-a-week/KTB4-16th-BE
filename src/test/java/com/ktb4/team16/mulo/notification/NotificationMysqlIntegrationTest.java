package com.ktb4.team16.mulo.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.friend.service.FriendCommandService;
import com.ktb4.team16.mulo.friend.service.FriendCommandTransactionalService;
import com.ktb4.team16.mulo.friend.service.SendFriendRequestResult;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxProcessor;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxScheduler;
import com.ktb4.team16.mulo.notification.service.NotificationService;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.service.MonthlyReportAiCallbackService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MySQL에서 원본·Outbox 결합, Savepoint 처리, 사용자별 API 조회를 검증한다. */
@SpringBootTest
class NotificationMysqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FriendCommandService friendCommandService;

    @Autowired
    private FriendCommandTransactionalService friendCommands;

    @Autowired
    private NotificationOutboxProcessor outboxProcessor;

    // 지연 이벤트는 테스트가 지정한 순서로 처리하고 예약 실행의 개입을 막는다.
    @MockitoBean
    private NotificationOutboxScheduler scheduler;

    @Autowired
    private MonthlyReportAiCallbackService reportCallbackService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();

    /** 테스트가 만든 Outbox·알림·친구 리소스·사용자를 FK 순서대로 제거한다. */
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("""
                    DELETE FROM outbox_events
                    WHERE event_type IN ('FRIEND_REQUEST', 'FRIEND_ACCEPTED', 'MONTHLY_REPORT_CREATED')
                        AND JSON_CONTAINS(payload, JSON_ARRAY(?), '$.recipientUserIds')
                    """, userId);
            jdbcTemplate.update("""
                    DELETE FROM outbox_events
                    WHERE event_id IN (
                        SELECT source_event_id FROM notifications WHERE user_id = ?
                    )
                    """, userId);
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM monthly_photo_scene_stats WHERE monthly_report_id IN "
                    + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM monthly_mood_stats WHERE monthly_report_id IN "
                    + "(SELECT monthly_report_id FROM monthly_reports WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM monthly_reports WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM friend_requests WHERE requester_id = ? OR addressee_id = ?",
                    userId, userId);
            jdbcTemplate.update("DELETE FROM friendships WHERE user_low_id = ? OR user_high_id = ?",
                    userId, userId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    /** 역방향 요청은 친구 관계 생성과 2개 알림까지 Outbox worker가 원자적으로 마친다. */
    @Test
    void mutualFriendRequestsCreateTwoNotificationsForOneCompletedEvent() throws InterruptedException {
        TestUser requester = insertUser("requester");
        TestUser addressee = insertUser("addressee");
        var pending = friendCommandService.sendFriendRequest(requester.id(), addressee.nickname());

        var result = friendCommandService.sendFriendRequest(addressee.id(), requester.nickname());

        assertThat(result).isInstanceOf(SendFriendRequestResult.BecameFriends.class);
        Long friendshipId = ((SendFriendRequestResult.BecameFriends) result).friendshipId();
        awaitCompleted("FRIEND_REQUEST", ((SendFriendRequestResult.Pending) pending).friendRequestId());
        awaitCompleted("FRIEND_ACCEPTED", friendshipId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE type = 'FRIEND_ACCEPTED' AND friendship_id = ?
                """, Integer.class, friendshipId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT source_event_id) FROM notifications
                WHERE type = 'FRIEND_ACCEPTED' AND friendship_id = ?
                """, Integer.class, friendshipId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM outbox_events
                WHERE event_type = 'FRIEND_ACCEPTED' AND aggregate_id = ?
                """, String.class, friendshipId)).isEqualTo("COMPLETED");
    }

    /** 취소 뒤 처리되는 요청 이벤트는 알림 없이 완료되며 반복 처리해도 재시도하지 않는다. */
    @Test
    void cancelledRequestSkipsLateNotificationAndCompletesWithoutRetry() {
        TestUser requester = insertUser("cancelSender");
        TestUser addressee = insertUser("cancelReceiver");
        var pending = friendCommands.sendFriendRequest(requester.id(), addressee.nickname());
        Long requestId = ((SendFriendRequestResult.Pending) pending.result()).friendRequestId();
        UUID eventId = pending.eventIds().getFirst();
        friendCommands.deleteFriendRequest(requester.id(), requestId);

        outboxProcessor.process(eventId);
        outboxProcessor.process(eventId);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE source_event_id = ?
                """, Integer.class, eventId.toString())).isZero();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, retry_count, last_error_code FROM outbox_events WHERE event_id = ?
                """, eventId.toString()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("retry_count", 0)
                .containsEntry("last_error_code", null);
    }

    /** 먼저 저장한 요청 알림은 취소 후에도 원문과 null 대상을 가진 이력으로 조회된다. */
    @Test
    void existingRequestNotificationRemainsAfterCancellationWithNullTarget() {
        TestUser requester = insertUser("historySender");
        TestUser addressee = insertUser("historyReceiver");
        var pending = friendCommands.sendFriendRequest(requester.id(), addressee.nickname());
        Long requestId = ((SendFriendRequestResult.Pending) pending.result()).friendRequestId();
        outboxProcessor.process(pending.eventIds().getFirst());
        var original = notificationService.getNotifications(addressee.id(), null, 20)
                .data().notifications().getFirst();
        assertThat(original.targetId()).isEqualTo(requestId);

        friendCommands.deleteFriendRequest(requester.id(), requestId);

        var remaining = notificationService.getNotifications(addressee.id(), null, 20)
                .data().notifications();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().notificationId()).isEqualTo(original.notificationId());
        assertThat(remaining.getFirst().targetId()).isNull();
        assertThat(remaining.getFirst().title()).isEqualTo(original.title());
        assertThat(remaining.getFirst().content()).isEqualTo(original.content());
        assertThat(remaining.getFirst().readAt()).isNull();
    }

    /** 친구 관계가 먼저 삭제돼도 수락 알림은 기존 정책대로 수신자별 이력을 보존한다. */
    @Test
    void acceptedNotificationsKeepHistoryWhenFriendshipWasDeletedBeforeProcessing() {
        TestUser requester = insertUser("acceptedSender");
        TestUser addressee = insertUser("acceptedReceiver");
        friendCommands.sendFriendRequest(requester.id(), addressee.nickname());
        var accepted = friendCommands.sendFriendRequest(addressee.id(), requester.nickname());
        Long friendshipId = ((SendFriendRequestResult.BecameFriends) accepted.result()).friendshipId();
        UUID eventId = accepted.eventIds().getFirst();
        friendCommands.deleteFriendship(requester.id(), friendshipId);

        outboxProcessor.process(eventId);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE source_event_id = ? AND type = 'FRIEND_ACCEPTED' AND friendship_id IS NULL
                """, Integer.class, eventId.toString())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM outbox_events WHERE event_id = ?
                """, String.class, eventId.toString())).isEqualTo("COMPLETED");
    }

    /** AI 회고 COMPLETED 변경과 월간 리포트 이벤트 및 알림이 같은 commit에서 연결된다. */
    @Test
    void completedAiRecapCreatesMonthlyReportNotification() throws InterruptedException {
        TestUser owner = insertUser("reportowner");
        jdbcTemplate.update("""
                INSERT INTO monthly_reports
                    (user_id, report_year, report_month, record_count, ai_recap_status)
                VALUES (?, 2026, 9, 4, 'PROCESSING')
                """, owner.id());
        Long reportId = jdbcTemplate.queryForObject("""
                SELECT monthly_report_id FROM monthly_reports WHERE user_id = ?
                """, Long.class, owner.id());

        reportCallbackService.apply(new MonthlyReportAiCallbackRequest(null, "job-1", 2026, 9,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                List.of(new MonthlyReportAiCallbackRequest.Result(owner.id(),
                        MonthlyReportAiCallbackRequest.Status.COMPLETED,
                        new MonthlyReportAiCallbackRequest.AiRecap("9월 회고"), List.of(), null))));

        awaitCompleted("MONTHLY_REPORT_CREATED", reportId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM outbox_events
                WHERE event_type = 'MONTHLY_REPORT_CREATED' AND aggregate_id = ?
                """, String.class, reportId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT content FROM notifications
                WHERE user_id = ? AND monthly_report_id = ?
                """, String.class, owner.id(), reportId))
                .isEqualTo("2026년 9월 리포트가 도착했어요.");
    }

    /** 기본 풀 3개에서도 동시 원본 commit 직후 Outbox 처리까지 끝나야 한다. */
    @Test
    void concurrentFriendRequestsCompleteOutboxProcessingWithDefaultPoolSize() throws Exception {
        List<TestUser> requesters = List.of(
                insertUser("poola"), insertUser("poolb"), insertUser("poolc"));
        List<TestUser> addressees = List.of(
                insertUser("targeta"), insertUser("targetb"), insertUser("targetc"));
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<SendFriendRequestResult>> results = new ArrayList<>();

        try {
            for (int index = 0; index < 3; index++) {
                TestUser requester = requesters.get(index);
                TestUser addressee = addressees.get(index);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작 신호를 받지 못했습니다.");
                    }
                    return friendCommandService.sendFriendRequest(
                            requester.id(), addressee.nickname());
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<SendFriendRequestResult> result : results) {
                assertThat(result.get(15, TimeUnit.SECONDS))
                        .isInstanceOf(SendFriendRequestResult.Pending.class);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        for (int index = 0; index < requesters.size(); index++) {
            Long requestId = jdbcTemplate.queryForObject("""
                    SELECT friend_request_id FROM friend_requests
                    WHERE requester_id = ? AND addressee_id = ?
                    """, Long.class, requesters.get(index).id(), addressees.get(index).id());
            awaitCompleted("FRIEND_REQUEST", requestId);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM outbox_events
                    WHERE event_type = 'FRIEND_REQUEST' AND aggregate_id = ?
                    """, String.class, requestId)).isEqualTo("COMPLETED");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications WHERE friend_request_id = ?
                    """, Integer.class, requestId)).isEqualTo(1);
        }
    }

    /** 소유자가 아닌 ID가 batch에 섞이면 일부 read_at 변경 없이 전체 요청을 거부한다. */
    @Test
    void batchReadRejectsMixedOwnershipWithoutPartialUpdate() {
        TestUser owner = insertUser("owner");
        TestUser other = insertUser("other");
        Long ownedId = insertNotification(owner.id(), "FRIEND_REQUEST", null);
        Long foreignId = insertNotification(other.id(), "FRIEND_REQUEST", null);

        assertThatThrownBy(() -> notificationService.markRead(owner.id(), List.of(ownedId, foreignId)))
                .isInstanceOf(NotificationDomainException.class);

        assertThat(readAt(ownedId)).isNull();
        assertThat(readAt(foreignId)).isNull();
    }

    /** 원본 외부 트랜잭션 rollback 시 친구 요청과 Outbox가 함께 rollback된다. */
    @Test
    void sourceRollbackAlsoRollsBackOutboxEvent() {
        TestUser requester = insertUser("rollbacka");
        TestUser addressee = insertUser("rollbackb");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            friendCommandService.sendFriendRequest(requester.id(), addressee.nickname());
            throw new IllegalStateException("rollback test");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM friend_requests WHERE requester_id = ? AND addressee_id = ?
                """, Integer.class, requester.id(), addressee.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                WHERE event_type = 'FRIEND_REQUEST'
                    AND JSON_EXTRACT(payload, '$.senderUserId') = ?
                """, Integer.class, requester.id())).isZero();
    }

    /** 비동기 알림 완료를 DB 상태로 확인한 뒤 사용자별 데이터를 정리한다. */
    private void awaitCompleted(String eventType, Long aggregateId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String status;
        do {
            status = jdbcTemplate.queryForObject("""
                    SELECT status FROM outbox_events
                    WHERE event_type = ? AND aggregate_id = ?
                    """, String.class, eventType, aggregateId);
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(25);
        } while (System.nanoTime() < deadline);
        assertThat(status).isEqualTo("COMPLETED");
    }

    /** 고유한 활성 사용자를 생성해 다른 테스트와 사용자 데이터가 겹치지 않게 한다. */
    private TestUser insertUser(String prefix) {
        String nickname = prefix.substring(0, Math.min(prefix.length(), 2))
                + UUID.randomUUID().toString().substring(0, 8);
        String email = nickname + "@example.test";
        jdbcTemplate.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, email);
        createdUserIds.add(userId);
        return new TestUser(userId, nickname);
    }

    /** API 조회·소유권 테스트용 기존 V7 notifications 행을 추가한다. */
    private Long insertNotification(Long userId, String type, LocalDateTime readAt) {
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content, read_at)
                VALUES (?, ?, 'test title', 'test content', ?)
                """, userId, type, readAt);
        return jdbcTemplate.queryForObject("""
                SELECT notification_id FROM notifications
                WHERE user_id = ? ORDER BY notification_id DESC LIMIT 1
                """, Long.class, userId);
    }

    /** 알림이 읽음 처리되지 않았는지 DB에서 직접 확인한다. */
    private LocalDateTime readAt(Long notificationId) {
        return jdbcTemplate.queryForObject("SELECT read_at FROM notifications WHERE notification_id = ?",
                LocalDateTime.class, notificationId);
    }

    /** 테스트 사용자 ID와 이벤트 당시 닉네임을 함께 보유한다. */
    private record TestUser(Long id, String nickname) {
    }
}
