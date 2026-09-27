package com.ktb4.team16.mulo.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.user.entity.User;
import org.junit.jupiter.api.Test;

class MonthlyReportTest {

    @Test
    void preparesAndCompletesMonthlyReportWithoutDowngradingLateFailure() {
        User user = User.signup("user@test.com", "hash", "사용자");

        MonthlyReport report = MonthlyReport.prepare(user, (short) 2026, (short) 9, 3, "뮤로");

        assertThat(report.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.PENDING);
        assertThat(report.getAiRecapText()).isNull();

        report.markProcessing();
        report.complete("9월 회고");
        report.markFailedUnlessCompleted();

        assertThat(report.getAiRecapStatus()).isEqualTo(MonthlyReport.AiRecapStatus.COMPLETED);
        assertThat(report.getAiRecapText()).isEqualTo("9월 회고");
    }
}
