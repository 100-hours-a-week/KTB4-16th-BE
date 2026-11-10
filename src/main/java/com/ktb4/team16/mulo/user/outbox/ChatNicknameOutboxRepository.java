package com.ktb4.team16.mulo.user.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class ChatNicknameOutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 닉네임 변경 트랜잭션 안에서 발행 대기 이벤트 한 건을 저장한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ChatNicknameChangedMessage message) {
        String payload = objectMapper.writeValueAsString(message);
        // 중요: 같은 사용자 버전의 중복이나 저장 오류는 전파해 닉네임 변경도 롤백한다.
        jdbcTemplate.update("""
                INSERT INTO chat_nickname_outbox (event_id, user_id, nickname_version, payload)
                VALUES (?, ?, ?, CAST(? AS JSON))
                """, message.eventId().toString(), message.userId(), message.nicknameVersion(), payload);
    }
}
