package com.ktb4.team16.mulo.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class IntegrationTestDatabaseGuard {
    private static final String DATABASE_NAME = "mulo_integration_test";

    private IntegrationTestDatabaseGuard() { }

    public static void verify(DataSource dataSource, JdbcTemplate jdbcTemplate) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(connection.getCatalog()).isEqualTo(DATABASE_NAME);
            assertThat(url).startsWith("jdbc:mysql://127.0.0.1:")
                    .contains("/" + DATABASE_NAME + "?");
            String mappedPort = url.substring("jdbc:mysql://127.0.0.1:".length()).split("/", 2)[0];
            assertThat(mappedPort).isNotEqualTo("3306");
            assertThat(url).isEqualTo(System.getenv("MULO_INTEGRATION_TEST_DB_URL"));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                    .isGreaterThanOrEqualTo(6);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT MAX(version) FROM flyway_schema_history WHERE success = 1", String.class))
                    .isEqualTo("6");
        }
    }
}
