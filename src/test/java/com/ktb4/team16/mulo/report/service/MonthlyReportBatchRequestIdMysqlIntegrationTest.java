package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.AiRecap;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Result;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Status;
import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportAiCallbackException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실제 MySQL에서 요청 ID 커밋과 이전 시도 콜백 거절을 검증한다. */
@SpringBootTest
class MonthlyReportBatchRequestIdMysqlIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MonthlyReportPreparationService preparation;
    @Autowired private MonthlyReportAiCallbackTransactionalService callbackService;
    private Long userId;

    // 테스트가 만든 리포트와 사용자만 제거한다.
    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbc.update("DELETE FROM monthly_reports WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    // 새 시도 ID가 DB에 저장되며 이전 시도의 결과가 적용되지 않음을 확인한다.
    @Test
    void persistsRequestIdAndRejectsStaleCallback() {
        String nickname = "br" + UUID.randomUUID().toString().substring(0, 8);
        String email = nickname + "@example.test";
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("""
                INSERT INTO monthly_reports
                    (user_id, report_year, report_month, record_count, ai_recap_status)
                VALUES (?, 2026, 8, 2, 'PENDING')
                """, userId);
        var batch = new MonthlyReportPreparationService.PreparedBatch(
                YearMonth.of(2026, 8), List.of(userId), 1, 0);
        String currentId = "4bc555fc-d70b-4060-8485-4e7664e164ab";

        preparation.reserveBatchRequestId(batch, currentId);

        assertThat(jdbc.queryForObject("""
                SELECT batch_request_id FROM monthly_reports WHERE user_id = ?
                """, String.class, userId)).isEqualTo(currentId);
        var stale = new MonthlyReportAiCallbackRequest(
                "older-batch", "job-1", 2026, 8,
                OffsetDateTime.parse("2026-09-01T03:00:00+09:00"),
                List.of(new Result(userId, Status.COMPLETED,
                        new AiRecap("오래된 회고"), List.of(), null)));

        assertThatThrownBy(() -> callbackService.apply(stale))
                .isInstanceOf(InvalidMonthlyReportAiCallbackException.class);
        assertThat(jdbc.queryForObject("""
                SELECT ai_recap_status FROM monthly_reports WHERE user_id = ?
                """, String.class, userId)).isEqualTo("PENDING");
    }
}
