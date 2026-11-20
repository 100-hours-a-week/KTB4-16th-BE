-- 닉네임 변경 알림을 기존 목록 유형에 추가한다.
ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'MONTHLY_REPORT_CREATED',
        'MATCH_COMPLETED',
        'FRIEND_REQUEST',
        'FRIEND_ACCEPTED',
        'DAILY_SONG_READY',
        'FRIEND_NICKNAME_CHANGED'
    ) NOT NULL;

-- 중복 전달 뒤 알림이 삭제되어도 원본 이벤트는 한 번만 처리되도록 이력을 보존한다.
CREATE TABLE nickname_notification_events (
    event_id CHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_nickname_notification_events PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
