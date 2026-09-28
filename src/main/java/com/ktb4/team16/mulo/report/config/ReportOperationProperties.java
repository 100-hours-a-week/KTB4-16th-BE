package com.ktb4.team16.mulo.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 배포 내부의 월간 리포트 수동 실행 요청을 인증하는 비밀값을 제공한다. */
@ConfigurationProperties("report.operation")
public record ReportOperationProperties(String token) {
    public ReportOperationProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Report operation token must be configured");
        }
    }
}
