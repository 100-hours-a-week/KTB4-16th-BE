package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.AiRecap;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Result;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Status;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Flyway 적용 후 실제 MySQL에 최대 길이 회고를 저장할 수 있는지 검증한다. */
@SpringBootTest
@Transactional
class MonthlyReportRecapLengthMysqlIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MonthlyReportAiCallbackTransactionalService callbackService;

    // 콜백 검증부터 JPA 쓰기와 DB 조회까지 300자 회고가 보존되는지 확인한다.
    @Test
    void persistsMaximumLengthRecapWithoutTruncation() {
        String nickname = "rl" + UUID.randomUUID().toString().substring(0, 8);
        String email = nickname + "@example.test";
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        Long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?",
                Long.class, email);
        jdbc.update("""
                INSERT INTO monthly_reports (user_id, report_year, report_month, record_count)
                VALUES (?, 2026, 8, 2)
                """, userId);
        String text = "가".repeat(300);

        callbackService.apply(new MonthlyReportAiCallbackRequest(null, "recap-length-test", 2026, 8,
                OffsetDateTime.parse("2026-09-01T03:00:00+09:00"),
                List.of(new Result(userId, Status.COMPLETED, new AiRecap(text), List.of(), null))));
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "SELECT ai_recap_text FROM monthly_reports WHERE user_id = ?",
                String.class, userId)).isEqualTo(text);
        assertThat(jdbc.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'monthly_reports'
                    AND column_name = 'ai_recap_text'
                """, Integer.class)).isEqualTo(300);
    }
}
