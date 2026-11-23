-- 닉네임 변경 알림의 공개 targetId를 목록 조회에서도 보존한다.
-- 다른 알림 유형은 대상 사용자가 없으므로 nullable로 두고, 대상 삭제 시 연결만 해제한다.
ALTER TABLE notifications
    ADD COLUMN target_user_id BIGINT NULL AFTER user_id,
    ADD CONSTRAINT fk_notifications_target_user
        FOREIGN KEY (target_user_id) REFERENCES users(user_id)
        ON DELETE SET NULL;
