-- 기존 사용자는 버전 0에서 시작하고 닉네임을 바꿀 때마다 버전이 증가한다.
ALTER TABLE users
    ADD COLUMN nickname_version BIGINT NOT NULL DEFAULT 0;

-- 닉네임 변경 시점의 스냅샷을 저장한다. user_id는 이벤트의 원본 식별자다.
CREATE TABLE chat_nickname_outbox (
    event_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    nickname_version BIGINT NOT NULL,
    payload JSON NOT NULL,
    status ENUM('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    retry_at DATETIME(6) NULL DEFAULT NULL,
    published_at DATETIME(6) NULL DEFAULT NULL,
    claim_token CHAR(36) NULL DEFAULT NULL,
    lease_until DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error_code VARCHAR(100) NULL DEFAULT NULL,

    CONSTRAINT pk_chat_nickname_outbox PRIMARY KEY (event_id),
    CONSTRAINT uk_chat_nickname_outbox_user_version UNIQUE (user_id, nickname_version),
    INDEX idx_chat_nickname_outbox_schedule (status, retry_at, created_at),
    INDEX idx_chat_nickname_outbox_lease (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
