package com.ktb4.team16.mulo.dailysong.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailySongTargetUserRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailySongTargetUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findUserIdsAtCutoff(LocalDateTime cutoff, long afterUserId, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("오늘의 노래 대상 조회 크기는 1 이상이어야 합니다.");
        }
        return jdbcTemplate.query("""
                SELECT user_id
                FROM users
                WHERE user_id > ? AND created_at <= ?
                    AND (deleted_at IS NULL OR deleted_at > ?)
                ORDER BY user_id
                LIMIT ?
                """, (result, row) -> result.getLong(1), afterUserId, cutoff, cutoff, pageSize);
    }
}
