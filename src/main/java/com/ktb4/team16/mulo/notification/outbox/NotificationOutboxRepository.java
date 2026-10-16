package com.ktb4.team16.mulo.notification.outbox;

import com.ktb4.team16.mulo.notification.entity.NotificationType;
import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Outbox 잠금·상태 갱신과 JDBC Savepoint를 사용하는 알림 INSERT를 담당한다. */
@Repository
public class NotificationOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 같은 JPA 트랜잭션에 참여하는 JDBC 접근을 구성한다. */
    public NotificationOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 같은 유형·aggregate 이벤트를 중복 저장하지 않고 기존 ID를 반환한다. */
    public UUID insertIfAbsent(NotificationEventType type, Long aggregateId, String payload) {
        for (int attempt = 0; attempt < 3; attempt++) {
            UUID candidate = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload)
                    VALUES (?, ?, ?, CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE event_id = event_id
                    """, candidate.toString(), type.name(), aggregateId, payload);
            List<String> existingIds = jdbcTemplate.query("""
                    SELECT event_id FROM outbox_events
                    WHERE event_type = ? AND aggregate_id = ?
                    """, (result, rowNum) -> result.getString(1), type.name(), aggregateId);
            if (!existingIds.isEmpty()) {
                return UUID.fromString(existingIds.getFirst());
            }
            // aggregate UNIQUE가 아니라 UUID PK 충돌인 경우 새 UUID로 제한 재시도한다.
        }
        throw new IllegalStateException("Outbox 이벤트 UUID 충돌 재시도 한도를 초과했습니다.");
    }

    /** 현재 예약 시각까지 도달한 PENDING 이벤트 ID를 생성 순으로 최대 개수만 조회한다. */
    public List<UUID> findDueEventIds(LocalDateTime now, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id FROM outbox_events
                WHERE status = 'PENDING'
                    AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ORDER BY created_at ASC, event_id ASC
                LIMIT ?
                """, (result, rowNum) -> UUID.fromString(result.getString(1)), now, limit);
    }

    /** 잠금을 얻을 수 있고 여전히 처리 가능한 이벤트 한 건을 SKIP LOCKED로 조회한다. */
    public Optional<OutboxEvent> lockDueEvent(UUID eventId, LocalDateTime now) {
        List<OutboxEvent> rows = jdbcTemplate.query("""
                SELECT event_id, event_type, aggregate_id, payload, retry_count, next_retry_at
                FROM outbox_events
                WHERE event_id = ? AND status = 'PENDING'
                    AND (next_retry_at IS NULL OR next_retry_at <= ?)
                FOR UPDATE SKIP LOCKED
                """, this::toOutboxEvent, eventId.toString(), now);
        return rows.stream().findFirst();
    }

    /** 모든 수신자 알림 저장이 끝난 이벤트를 COMPLETED로 전환한다. */
    public int markCompleted(UUID eventId, LocalDateTime processedAt) {
        return jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'COMPLETED', next_retry_at = NULL, processed_at = ?
                WHERE event_id = ? AND status = 'PENDING'
                """, processedAt, eventId.toString());
    }

    /** 영구 실패 또는 재시도 예약 상태와 마지막 오류 코드를 저장한다. */
    public int markFailure(UUID eventId, int retryCount, LocalDateTime nextRetryAt,
            String errorCode, LocalDateTime failedAt, boolean permanent) {
        String status = permanent ? "FAILED" : "PENDING";
        return jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = ?, retry_count = ?, next_retry_at = ?,
                    last_error_code = ?, last_error_at = ?
                WHERE event_id = ? AND status = 'PENDING'
                """, status, retryCount, nextRetryAt, errorCode, failedAt, eventId.toString());
    }

    /** 친구 알림 수신자 전체를 ID 순서로 공유 잠금해 이후 대상 잠금과 순서를 맞춘다. */
    public void lockExistingRecipientsInIdOrder(List<Long> recipientIds) {
        recipientIds.stream().distinct().sorted().forEach(userId -> jdbcTemplate.queryForList("""
                SELECT user_id FROM users WHERE user_id = ? FOR SHARE
                """, Long.class, userId));
    }

    /** 대상 Target이 같은 DB에 존재하면 공유 잠금으로 INSERT와 동시 삭제를 직렬화한다. */
    public Long lockExistingTarget(NotificationType type, Long targetId) {
        if (targetId == null) {
            return null;
        }
        String sql = switch (type) {
            case MONTHLY_REPORT_CREATED -> "SELECT monthly_report_id FROM monthly_reports "
                    + "WHERE monthly_report_id = ? FOR SHARE";
            case FRIEND_REQUEST -> "SELECT friend_request_id FROM friend_requests "
                    + "WHERE friend_request_id = ? FOR SHARE";
            case FRIEND_ACCEPTED -> "SELECT friendship_id FROM friendships "
                    + "WHERE friendship_id = ? FOR SHARE";
            case MATCH_COMPLETED -> null;
        };
        if (sql == null) {
            return targetId;
        }
        List<Long> found = jdbcTemplate.query(sql,
                (result, rowNum) -> result.getLong(1), targetId);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Outbox event ID를 포함한 수신자별 알림을 트랜잭션에 INSERT한다. */
    public void insertNotification(UUID eventId, NotificationDraft draft) {
        Long targetId = lockExistingTarget(draft.type(), draft.targetId());
        jdbcTemplate.update("""
                INSERT INTO notifications (
                    user_id, type, monthly_report_id, chat_room_id, friendship_id,
                    friend_request_id, source_event_id, title, content
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, draft.recipientUserId(), draft.type().name(),
                draft.type() == NotificationType.MONTHLY_REPORT_CREATED ? targetId : null,
                draft.type() == NotificationType.MATCH_COMPLETED ? targetId : null,
                draft.type() == NotificationType.FRIEND_ACCEPTED ? targetId : null,
                draft.type() == NotificationType.FRIEND_REQUEST ? targetId : null,
                eventId.toString(), draft.title(), draft.content());
    }

    /** JDBC 결과 행의 DB 값만 worker 내부 record로 변환한다. */
    private OutboxEvent toOutboxEvent(ResultSet result, int rowNum) throws SQLException {
        String nextRetry = result.getString("next_retry_at");
        return new OutboxEvent(UUID.fromString(result.getString("event_id")),
                NotificationEventType.valueOf(result.getString("event_type")),
                result.getLong("aggregate_id"), result.getString("payload"),
                result.getInt("retry_count"), nextRetry == null ? null
                        : result.getTimestamp("next_retry_at").toLocalDateTime());
    }
}
