package com.ktb4.team16.mulo.report.batch;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 현재 월 시험이 local 프로파일만으로 운영 DB/AI를 호출하지 않도록 격리를 확인한다. */
@Component
public class MonthlyReportTestGuard {
    private final Environment environment;
    private final DataSource dataSource;
    private final AiProperties ai;

    /** 검증할 프로파일·실제 DB 메타데이터·AI 주소를 연결한다. */
    public MonthlyReportTestGuard(Environment environment, DataSource dataSource, AiProperties ai) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.ai = ai;
    }

    /** loopback 테스트 DB와 loopback AI인 경우에만 현재 월 실행을 허용한다. */
    public void verify() {
        boolean profile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(value -> "local".equals(value) || "test".equals(value));
        String aiHost = ai.baseUrl().getHost();
        if (!profile || !("localhost".equals(aiHost) || "127.0.0.1".equals(aiHost))) {
            throw new ReportBatchException("REPORT_TEST_ENVIRONMENT_REQUIRED");
        }
        try (var connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String url = connection.getMetaData().getURL();
            if (catalog == null || !(catalog.endsWith("_test") || catalog.endsWith("_it"))
                    || !(url.startsWith("jdbc:mysql://127.0.0.1:") || url.startsWith("jdbc:mysql://localhost:"))) {
                throw new ReportBatchException("REPORT_TEST_ENVIRONMENT_REQUIRED");
            }
        } catch (SQLException exception) {
            throw new ReportBatchException("REPORT_TEST_ENVIRONMENT_REQUIRED");
        }
    }
}
