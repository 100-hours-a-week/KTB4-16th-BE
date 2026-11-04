package com.ktb4.team16.mulo.report.batch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 현재 월 시험의 프로파일·DB·AI 격리 경계를 검증한다. */
class MonthlyReportTestGuardTest {
    /** 운영 프로파일은 DB에 접근하기 전에 거절한다. */
    @Test
    void rejectsProductionProfile() {
        DataSource source = mock(DataSource.class);
        var guard = new MonthlyReportTestGuard(new MockEnvironment(), source,
                ai("http://localhost:8089"));
        assertThatThrownBy(guard::verify).isInstanceOf(ReportBatchException.class)
                .hasMessage("REPORT_TEST_ENVIRONMENT_REQUIRED");
        verifyNoInteractions(source);
    }

    /** 테스트 프로파일이라도 외부 AI 요청은 허용하지 않는다. */
    @Test
    void rejectsExternalAi() {
        DataSource source = mock(DataSource.class);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        var guard = new MonthlyReportTestGuard(environment, source, ai("https://ai.example.com"));
        assertThatThrownBy(guard::verify).isInstanceOf(ReportBatchException.class);
        verifyNoInteractions(source);
    }

    /** 로컬 테스트 스키마와 로컬 AI의 조합은 허용한다. */
    @Test
    void acceptsIsolatedDatabase() throws Exception {
        guard("mulo_test", "jdbc:mysql://127.0.0.1:3321/mulo_test").verify();
    }

    /** 로컬 프로파일을 켰더라도 운영 스키마 또는 원격 DB는 거절한다. */
    @Test
    void rejectsProductionCatalogAndRemoteDatabase() throws Exception {
        assertThatThrownBy(guard("mulo", "jdbc:mysql://127.0.0.1:3306/mulo")::verify)
                .isInstanceOf(ReportBatchException.class);
        assertThatThrownBy(guard("mulo_test", "jdbc:mysql://database.internal:3306/mulo_test")::verify)
                .isInstanceOf(ReportBatchException.class);
    }

    /** 실제 접속 메타데이터를 사용한다는 경계를 fixture로 구성한다. */
    private MonthlyReportTestGuard guard(String catalog, String url) throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn(catalog);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(url);
        return new MonthlyReportTestGuard(environment, source, ai("http://localhost:8089"));
    }

    /** 외부 호출을 하지 않고 대상 주소 검증에 필요한 설정만 만든다. */
    private AiProperties ai(String url) {
        return new AiProperties(URI.create(url), "test", Duration.ofSeconds(1), Duration.ofSeconds(1), true);
    }
}
