package com.ktb4.team16.mulo.report.client;

// AI 배치 요청 또는 AI가 반환한 계약 위반을 외부 상세 정보 없이 전달한다.
public class MonthlyReportAiException extends RuntimeException {
    public MonthlyReportAiException() {
        super("AI monthly report request failed");
    }

    public MonthlyReportAiException(Throwable cause) {
        super("AI monthly report request failed", cause);
    }
}
