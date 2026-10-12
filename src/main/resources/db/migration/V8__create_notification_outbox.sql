-- Outbox는 원본 비즈니스 트랜잭션과 알림 처리 사이의 이벤트를 보존한다.
-- aggregate_id는 원본 ID 스냅샷이며 이벤트·대상 테이블 FK를 두지 않는다.
CREATE TABLE outbox_events (
    event_id CHAR(36) NOT NULL,
    event_type ENUM(
        'MONTHLY_REPORT_CREATED',
        'MATCH_COMPLETED',
        'FRIEND_REQUEST',
        'FRIEND_ACCEPTED'
    ) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload JSON NOT NULL,
    status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL DEFAULT NULL,
    last_error_code VARCHAR(100) NULL DEFAULT NULL,
    last_error_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL DEFAULT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (event_id),
    CONSTRAINT uk_outbox_events_type_aggregate UNIQUE (event_type, aggregate_id),
    INDEX idx_outbox_events_schedule (status, next_retry_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
