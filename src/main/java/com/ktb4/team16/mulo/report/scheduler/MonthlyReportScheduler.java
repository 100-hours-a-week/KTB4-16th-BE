package com.ktb4.team16.mulo.report.scheduler;

import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {
    private final MonthlyReportGenerationService generationService;

    // 매월 1일 01:00 KST에 완료된 지난달 월간 리포트를 생성한다.
    @Scheduled(cron = "0 0 1 1 * *", zone = "Asia/Seoul")
    public void generatePreviousMonthReports() {
        generationService.generatePreviousMonthReports();
    }
}
