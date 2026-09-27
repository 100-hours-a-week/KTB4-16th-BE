package com.ktb4.team16.mulo.report.dto.response;

import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import java.util.List;

public record MonthlyReportListResponse(String message, Data data) {
    public record Data(List<Report> reports) {
    }
    public record Report(Long monthlyReportId, int year, int month, int recordCount,
            String aiRecapStatus) {
        // 저장된 월간 스냅샷을 목록 API 계약으로 변환한다.
        public static Report from(MonthlyReport report) {
            return new Report(report.getMonthlyReportId(), report.getReportYear(), report.getReportMonth(),
                    report.getRecordCount(), report.getAiRecapStatus().name());
        }
    }
}
