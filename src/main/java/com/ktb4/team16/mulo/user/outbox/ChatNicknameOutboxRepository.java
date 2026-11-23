package com.ktb4.team16.mulo.user.outbox;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class ChatNicknameOutboxRepository {
    private static final int CLAIM_LEASE_MINUTES = 2;
    private static final int MAX_PUBLISH_ATTEMPTS = 10;
    private static final long INITIAL_RETRY_DELAY_SECONDS = 5;
    private static final long MAX_RETRY_DELAY_SECONDS = 300;
    private static final int MAX_BACKOFF_DOUBLINGS = 6;

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

    record Claim(String eventId, String payload, String token, int attempt) {}

    /** 다른 worker가 잡은 행을 건너뛰고 만료된 선점 또는 발행 대기 한 건을 회수한다. */
    @Transactional
    public Optional<Claim> claimNext() {
        var token = UUID.randomUUID().toString();
        var claims = jdbcTemplate.query("""
                SELECT event_id, payload, attempt_count FROM chat_nickname_outbox
                WHERE (status = 'PENDING' AND (retry_at IS NULL OR retry_at <= CURRENT_TIMESTAMP(6)))
                   OR (status = 'PUBLISHING' AND lease_until <= CURRENT_TIMESTAMP(6))
                ORDER BY created_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (row, index) -> new Claim(row.getString(1), row.getString(2), token, row.getInt(3) + 1));
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        var claim = claims.getFirst();
        // 두 목적지의 확인 대기(각 10초)보다 길게 선점하고, 중단된 작업은 만료 후 회수한다.
        jdbcTemplate.update("""
                UPDATE chat_nickname_outbox SET status = 'PUBLISHING', claim_token = ?,
                    lease_until = TIMESTAMPADD(MINUTE, ?, CURRENT_TIMESTAMP(6)), attempt_count = ?
                WHERE event_id = ?
                """, token, CLAIM_LEASE_MINUTES, claim.attempt(), claim.eventId());
        return Optional.of(claim);
    }

    /** 활성 목적지의 발행 확인을 받은 현재 소유자만 완료 시각을 남긴다. */
    public void published(Claim claim) {
        jdbcTemplate.update("""
                UPDATE chat_nickname_outbox SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(6),
                    claim_token = NULL, lease_until = NULL, retry_at = NULL, last_error_code = NULL
                WHERE event_id = ? AND status = 'PUBLISHING' AND claim_token = ?
                """, claim.eventId(), claim.token());
    }

    /** 확인 실패를 지수 지연으로 재예약하고 열 번째 실패부터 운영 재처리를 기다린다. */
    public void retry(Claim claim, String error) {
        // 5→10→20→40→80→160→300초로 증가시키고 이후에는 300초를 유지한다.
        long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS,
                INITIAL_RETRY_DELAY_SECONDS << Math.min(claim.attempt() - 1, MAX_BACKOFF_DOUBLINGS));
        jdbcTemplate.update("""
                UPDATE chat_nickname_outbox SET status = IF(attempt_count >= ?, 'FAILED', 'PENDING'),
                    retry_at = IF(attempt_count >= ?, NULL, TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6))),
                    claim_token = NULL, lease_until = NULL, last_error_code = ?
                WHERE event_id = ? AND status = 'PUBLISHING' AND claim_token = ?
                """, MAX_PUBLISH_ATTEMPTS, MAX_PUBLISH_ATTEMPTS, delaySeconds, error, claim.eventId(), claim.token());
    }
}
