package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.friend.service.FriendCommandTransactionalService;
import com.ktb4.team16.mulo.friend.service.SendFriendRequestResult;
import com.ktb4.team16.mulo.notification.entity.NotificationType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 MySQL에서 SQL 왕복 수와 격리 수준별 요청 세대·현재 읽기를 검증한다. */
@SpringBootTest
class NotificationConditionalInsertMysqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NotificationOutboxRepository outbox;
    @Autowired
    private FriendCommandTransactionalService commands;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // 이벤트 처리 시점은 테스트에서 지정하고 예약 실행만 차단한다.
    @MockitoBean
    private NotificationOutboxScheduler scheduler;

    private final List<Long> userIds = new ArrayList<>();

    /** 테스트가 만든 이벤트·알림·요청·사용자만 외래 키 순서로 정리한다. */
    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update("""
                    DELETE FROM outbox_events
                    WHERE event_type = 'FRIEND_REQUEST'
                      AND JSON_CONTAINS(payload, JSON_ARRAY(?), '$.recipientUserIds')
                    """, userId);
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM friend_requests WHERE requester_id = ? OR addressee_id = ?",
                    userId, userId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    /** 별도 대상 SELECT가 재도입되면 실제 MySQL의 문장 실행 수로 회귀를 탐지한다. */
    @ParameterizedTest
    @ValueSource(ints = {TransactionDefinition.ISOLATION_READ_COMMITTED,
            TransactionDefinition.ISOLATION_REPEATABLE_READ})
    void pendingRequestIsValidatedAndInsertedInOneStatement(int isolation) {
        RequestFixture fixture = createRequest();
        transaction(isolation).executeWithoutResult(status -> {
            // 운영 worker와 같이 수신자 잠금을 먼저 얻은 후 대상 확인·저장 부분만 측정한다.
            outbox.lockExistingRecipientsInIdOrder(List.of(fixture.addressee().id()));
            long before = selectAndInsertCount();
            outbox.insertNotification(fixture.eventId(), draft(fixture));
            long statements = selectAndInsertCount() - before;
            assertThat(statements).as("요청 확인과 알림 저장의 DB 문장 실행 수").isEqualTo(1);
        });

        assertThat(jdbc.queryForMap("""
                SELECT user_id, friend_request_id, title, content
                FROM notifications WHERE source_event_id = ?
                """, fixture.eventId().toString()))
                .containsEntry("user_id", fixture.addressee().id())
                .containsEntry("friend_request_id", fixture.requestId())
                .containsEntry("title", "친구 요청")
                .containsEntry("content", "조건부 INSERT 검증");
    }

    /** 과거 snapshot과 같은 쌍의 새 요청이 있어도 종료된 원래 이벤트를 되살리지 않는다. */
    @ParameterizedTest
    @ValueSource(ints = {TransactionDefinition.ISOLATION_READ_COMMITTED,
            TransactionDefinition.ISOLATION_REPEATABLE_READ})
    void oldSnapshotAndNewRequestDoNotReviveCancelledEvent(int isolation) {
        RequestFixture original = createRequest();
        try (var executor = Executors.newSingleThreadExecutor()) {
            RequestFixture latest = transaction(isolation).execute(status -> {
                // RR에서 의도적으로 오래된 consistent-read snapshot을 먼저 만든다.
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM friend_requests WHERE friend_request_id = ?
                        """, Integer.class, original.requestId())).isEqualTo(1);
                var replacement = executor.submit(() -> {
                    commands.deleteFriendRequest(original.requester().id(), original.requestId());
                    return sendRequest(original.requester(), original.addressee());
                });
                RequestFixture current;
                try {
                    current = replacement.get(15, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("별도 트랜잭션의 취소·재요청 실패", failure);
                }
                outbox.lockExistingRecipientsInIdOrder(List.of(original.addressee().id()));
                outbox.insertNotification(original.eventId(), draft(original));
                outbox.insertNotification(current.eventId(), draft(current));
                return current;
            });

            assertThat(latest).isNotNull();
            assertThat(latest.requestId()).isNotEqualTo(original.requestId());
            assertThat(notificationCount(original.eventId())).isZero();
            assertThat(notificationCount(latest.eventId())).isEqualTo(1);
        }
    }

    /** 같은 JDBC 트랜잭션 연결의 SELECT·INSERT 계수만 더하며 SHOW 자체는 포함하지 않는다. */
    private long selectAndInsertCount() {
        return jdbc.queryForList("""
                SHOW SESSION STATUS
                WHERE Variable_name IN ('Com_select', 'Com_insert', 'Com_insert_select')
                """).stream().mapToLong(row -> Long.parseLong(row.get("Value").toString())).sum();
    }

    /** 명시한 격리 수준으로 Repository가 참여할 실제 DB 트랜잭션을 구성한다. */
    private TransactionTemplate transaction(int isolation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(isolation);
        return template;
    }

    /** 검증된 이벤트를 통해 원래 요청 ID와 수신자를 가진 알림 초안을 만든다. */
    private NotificationDraft draft(RequestFixture fixture) {
        return new NotificationDraft(fixture.addressee().id(), NotificationType.FRIEND_REQUEST,
                fixture.requestId(), "친구 요청", "조건부 INSERT 검증");
    }

    /** 지정 이벤트에서 실제로 생성된 알림 수를 확인한다. */
    private int notificationCount(UUID eventId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id = ?",
                Integer.class, eventId.toString());
    }

    /** 서로 다른 활성 사용자와 아직 worker가 처리하지 않은 실제 요청 이벤트를 준비한다. */
    private RequestFixture createRequest() {
        return sendRequest(insertUser("sqlA"), insertUser("sqlB"));
    }

    /** 실제 친구 명령을 실행하고 새 요청 세대와 Outbox ID를 묶어 반환한다. */
    private RequestFixture sendRequest(TestUser requester, TestUser addressee) {
        var result = commands.sendFriendRequest(requester.id(), addressee.nickname());
        long requestId = ((SendFriendRequestResult.Pending) result.result()).friendRequestId();
        return new RequestFixture(requester, addressee, requestId, result.eventIds().getFirst());
    }

    /** 고유한 테스트 사용자 한 명을 생성하고 정리 대상에 등록한다. */
    private TestUser insertUser(String prefix) {
        String nickname = prefix + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                nickname + "@example.test", "test-hash", nickname);
        Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE nickname = ?", Long.class, nickname);
        userIds.add(id);
        return new TestUser(id, nickname);
    }

    private record TestUser(Long id, String nickname) { }

    private record RequestFixture(TestUser requester, TestUser addressee, Long requestId, UUID eventId) { }
}
