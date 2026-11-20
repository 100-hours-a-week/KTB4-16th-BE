package com.ktb4.team16.mulo.notification.repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 MQ 이벤트의 멱등 이력과 수신자별 알림을 저장한다. */
@Repository
@RequiredArgsConstructor
public class NicknameFriendNotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    /** 처리 이력 PK를 먼저 얻어 중복 배달은 한 트랜잭션만 알림 생성 단계에 진입시킨다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claimEvent(UUID eventId) {
        return jdbcTemplate.update("""
                INSERT IGNORE INTO nickname_notification_events (event_id) VALUES (?)
                """, eventId.toString()) == 1;
    }

    /** 활성 친구 한 명에게 원본 eventId를 보존한 알림을 저장한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int insertNotification(UUID eventId, Long recipientUserId, Long changedUserId, String nickname) {
        String content = "‘%s’님이 닉네임을 변경했어요.".formatted(nickname);
        return jdbcTemplate.update("""
                INSERT INTO notifications (user_id, target_user_id, type, source_event_id, title, content)
                VALUES (?, ?, 'FRIEND_NICKNAME_CHANGED', ?, '친구 닉네임 변경', ?)
                """, recipientUserId, changedUserId, eventId.toString(), content);
    }
}
