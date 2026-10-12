-- 기존 AI 알림 이력이 있으면 아래의 유형·컬럼 제거 전에 마이그레이션을 중단한다.
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_no_ai_playlist CHECK (type <> 'AI_PLAYLIST');

-- 신규 이벤트의 사용자별 중복을 막고, friendship FK의 대체 지원 인덱스를 먼저 만든다.
ALTER TABLE notifications
    ADD COLUMN source_event_id CHAR(36) NULL,
    ADD CONSTRAINT uk_notifications_source_event_user UNIQUE (source_event_id, user_id),
    ADD INDEX idx_notifications_friendship (friendship_id);

-- 같은 친구 관계의 두 사용자에게 알림을 허용하되 FK는 보존한다.
ALTER TABLE notifications
    DROP INDEX uk_notifications_friendship;

-- AI 플레이리스트 알림 연결만 제거하고 플레이리스트 원본 데이터는 변경하지 않는다.
ALTER TABLE notifications
    DROP FOREIGN KEY fk_notifications_playlist,
    DROP INDEX uk_notifications_playlist,
    DROP COLUMN recommendation_playlist_id;

-- CHECK를 참조한 ENUM 변경과 임시 CHECK 제거를 같은 DDL에서 수행한다.
ALTER TABLE notifications
    MODIFY COLUMN type ENUM(
        'MONTHLY_REPORT_CREATED',
        'MATCH_COMPLETED',
        'FRIEND_REQUEST',
        'FRIEND_ACCEPTED'
    ) NOT NULL,
    DROP CHECK chk_notifications_no_ai_playlist;
