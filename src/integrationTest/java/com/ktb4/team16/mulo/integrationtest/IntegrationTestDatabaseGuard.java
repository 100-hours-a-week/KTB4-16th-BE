package com.ktb4.team16.mulo.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

public final class IntegrationTestDatabaseGuard {
    private static final String DATABASE_NAME = "mulo_integration_test";

    /** 격리 DB 검증은 정적 유틸리티로만 사용한다. */
    private IntegrationTestDatabaseGuard() { }

    /** 격리 접속 정보를 확인하고 현재 마이그레이션 파일과 DB 적용 이력을 검증한다. */
    public static void verify(DataSource dataSource, JdbcTemplate jdbcTemplate) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(connection.getCatalog()).isEqualTo(DATABASE_NAME);
            assertThat(url).startsWith("jdbc:mysql://127.0.0.1:")
                    .contains("/" + DATABASE_NAME + "?");
            String mappedPort = url.substring("jdbc:mysql://127.0.0.1:".length()).split("/", 2)[0];
            assertThat(mappedPort).isNotEqualTo("3306");
            assertThat(url).isEqualTo(System.getenv("MULO_INTEGRATION_TEST_DB_URL"));
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(11);
        // 버전 숫자 고정 없이 체크섬·미적용·알 수 없는 이력까지 현재 파일과 대조한다.
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .ignoreMigrationPatterns(new String[0])
                .load()
                .validate();
    }
}
