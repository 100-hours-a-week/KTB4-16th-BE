package com.ktb4.team16.mulo.integrationtest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IntegrationTestDatabaseGuardTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private DatabaseMetaData metadata;
    @Mock private JdbcTemplate jdbcTemplate;

    /** 격리 DB 이름·주소·포트가 아니면 마이그레이션 이력을 읽기 전에 거절한다. */
    @ParameterizedTest
    @CsvSource({
            "mulo,jdbc:mysql://127.0.0.1:3340/mulo?useSSL=false",
            "mulo_integration_test,jdbc:mysql://db.example.test:3340/mulo_integration_test?useSSL=false",
            "mulo_integration_test,jdbc:mysql://127.0.0.1:3306/mulo_integration_test?useSSL=false"
    })
    void rejectsUnsafeDatabaseBeforeReadingMigrationHistory(String catalog, String url) throws Exception {
        prepareConnection(catalog, url);

        assertThatThrownBy(() -> IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate))
                .isInstanceOf(AssertionError.class);

        verifyNoInteractions(jdbcTemplate);
    }

    /** 실제 접속 URL이 명시적으로 지정한 테스트 URL과 다르면 거절한다. */
    @Test
    void rejectsConnectionDifferentFromExplicitTestUrl() throws Exception {
        String configuredUrl = System.getenv("MULO_INTEGRATION_TEST_DB_URL");
        String url = (configuredUrl == null
                ? "jdbc:mysql://127.0.0.1:3340/mulo_integration_test?useSSL=false" : configuredUrl)
                + "&unexpected=" + UUID.randomUUID();
        prepareConnection("mulo_integration_test", url);

        assertThatThrownBy(() -> IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate))
                .isInstanceOf(AssertionError.class);

        verifyNoInteractions(jdbcTemplate);
    }

    /** 실제 DB 접속 없이 가드에 전달되는 JDBC 연결 정보만 준비한다. */
    private void prepareConnection(String catalog, String url) throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(url);
        when(connection.getCatalog()).thenReturn(catalog);
    }
}
