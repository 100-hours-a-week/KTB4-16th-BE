package com.ktb4.team16.mulo.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 독립 MySQL 스키마에서 알림 V7 데이터 보존과 V8 Outbox 구조를 검증한다. */
class NotificationMigrationMysqlIntegrationTest {

    private String baseUrl;
    private String username;
    private String password;
    private String schema;
    private JdbcTemplate admin;
    private JdbcTemplate db;

    /** 테스트마다 무작위 스키마를 만들고 공유 DB에는 접속하지 않는다. */
    @BeforeEach
    void createIsolatedSchema() {
        baseUrl = System.getenv("MULO_NOTIFICATION_IT_DB_URL");
        username = System.getenv("MULO_NOTIFICATION_IT_DB_USERNAME");
        password = System.getenv("MULO_NOTIFICATION_IT_DB_PASSWORD");
        assumeTrue(baseUrl != null && baseUrl.endsWith("/")
                && username != null && password != null,
                "A dedicated MySQL server URL ending in / and credentials are required");
        schema = "mulo_notification_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        admin = new JdbcTemplate(new DriverManagerDataSource(baseUrl, username, password));
        admin.execute("CREATE DATABASE " + schema);
        db = new JdbcTemplate(new DriverManagerDataSource(baseUrl + schema, username, password));
    }

    /** 이 테스트가 만든 스키마만 제거한다. */
    @AfterEach
    void dropIsolatedSchema() {
        if (schema != null && admin != null) {
            admin.execute("DROP DATABASE " + schema);
        }
    }

    /** AI 알림 행이 있으면 V7은 FK와 컬럼을 제거하기 전에 실패해야 한다. */
    @Test
    void aiPlaylistHistoryStopsMigrationBeforeSchemaRemoval() {
        migrateThroughV6();
        long userId = insertUser("oldai");
        insertNotification(userId, "AI_PLAYLIST", null, null);

        assertThatThrownBy(this::migrateLatest).isInstanceOf(Exception.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'AI_PLAYLIST'", Integer.class))
                .isEqualTo(1);
        assertThat(columnExists("recommendation_playlist_id")).isTrue();
        assertThat(foreignKeyExists("fk_notifications_playlist")).isTrue();
    }

    /** 비 AI 이력을 보존하면서 두 수신자, 이벤트 중복 방지, 기존 FK를 검증한다. */
    @Test
    void migrationPreservesHistoryAndEnforcesRecipientIdempotency() {
        migrateThroughV6();
        long first = insertUser("first");
        long second = insertUser("second");
        db.update("INSERT INTO friendships (user_low_id, user_high_id) VALUES (?, ?)", first, second);
        long friendshipId = db.queryForObject("SELECT friendship_id FROM friendships", Long.class);
        insertNotification(first, "FRIEND_ACCEPTED", friendshipId, null);

        migrateLatest();

        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id IS NULL", Integer.class))
                .isEqualTo(1);
        assertThat(columnExists("recommendation_playlist_id")).isFalse();
        assertThat(foreignKeyExists("fk_notifications_playlist")).isFalse();
        assertThat(foreignKeyExists("fk_notifications_friendship")).isTrue();
        assertThatThrownBy(() -> insertNotification(second, "AI_PLAYLIST", null, null))
                .isInstanceOf(Exception.class);
        assertThat(indexIsUnique("uk_notifications_friendship")).isNull();
        assertThat(indexIsUnique("idx_notifications_friendship")).isFalse();
        assertThat(indexIsUnique("uk_notifications_source_event_user")).isTrue();

        String eventId = UUID.randomUUID().toString();
        insertNotification(first, "FRIEND_ACCEPTED", friendshipId, eventId);
        insertNotification(second, "FRIEND_ACCEPTED", friendshipId, eventId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE friendship_id = ?", Integer.class,
                friendshipId)).isEqualTo(3);
        assertThatThrownBy(() -> insertNotification(first, "FRIEND_ACCEPTED", friendshipId, eventId))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> insertNotification(second, "FRIEND_ACCEPTED", friendshipId + 1000, null))
                .isInstanceOf(Exception.class);
        insertNotification(second, "FRIEND_ACCEPTED", friendshipId, null);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id IS NULL", Integer.class))
                .isEqualTo(2);
        db.update("DELETE FROM friendships WHERE friendship_id = ?", friendshipId);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE friendship_id IS NULL", Integer.class))
                .isEqualTo(4);
    }

    /** V8 Outbox가 사건 멱등성과 처리 예약에 필요한 DB 제약을 갖는지 검증한다. */
    @Test
    void createsOutboxTableWithPayloadRetryAndSchedulingConstraints() {
        migrateLatest();

        assertThat(tableExists("outbox_events")).isTrue();
        assertThat(columnExists("outbox_events", "payload")).isTrue();
        assertThat(columnExists("outbox_events", "next_retry_at")).isTrue();
        assertThat(indexColumns("uk_outbox_events_type_aggregate"))
                .containsExactly("event_type", "aggregate_id");
        assertThat(indexColumns("idx_outbox_events_schedule"))
                .containsExactly("status", "next_retry_at", "created_at");
    }

    /** 기존 V1~V6을 재현한다. */
    private void migrateThroughV6() {
        Flyway.configure().dataSource(baseUrl + schema, username, password)
                .target("6").load().migrate();
    }

    /** 전체 마이그레이션을 적용한다. */
    private void migrateLatest() {
        flyway().migrate();
    }

    /** 테스트 스키마용 Flyway를 구성한다. */
    private Flyway flyway() {
        return Flyway.configure().dataSource(baseUrl + schema, username, password).load();
    }

    /** FK를 충족하는 테스트 사용자를 생성한다. */
    private long insertUser(String prefix) {
        String nickname = prefix + UUID.randomUUID().toString().substring(0, 3);
        db.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                nickname + "@example.test", "test-hash", nickname);
        return db.queryForObject("SELECT user_id FROM users WHERE nickname = ?", Long.class, nickname);
    }

    /** 원본 및 신규 형식의 알림 행을 추가한다. */
    private void insertNotification(long userId, String type, Long friendshipId, String eventId) {
        if (eventId == null && !columnExists("source_event_id")) {
            db.update("INSERT INTO notifications (user_id, type, friendship_id, title, content) "
                    + "VALUES (?, ?, ?, 'title', 'content')", userId, type, friendshipId);
        } else {
            db.update("INSERT INTO notifications (user_id, type, friendship_id, source_event_id, title, content) "
                    + "VALUES (?, ?, ?, ?, 'title', 'content')", userId, type, friendshipId, eventId);
        }
    }

    /** 삭제 대상 컬럼의 존재 여부를 조회한다. */
    private boolean columnExists(String columnName) {
        return db.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'notifications' AND column_name = ?",
                Integer.class, schema, columnName) > 0;
    }

    /** 지정 테이블이 현재 격리 스키마에 존재하는지 조회한다. */
    private boolean tableExists(String tableName) {
        return db.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = ?", Integer.class, schema, tableName) > 0;
    }

    /** 지정 테이블에 필요한 컬럼이 존재하는지 조회한다. */
    private boolean columnExists(String tableName, String columnName) {
        return db.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, schema, tableName, columnName) > 0;
    }

    /** 인덱스의 컬럼 순서를 확인한다. */
    private List<String> indexColumns(String indexName) {
        return db.query("SELECT column_name FROM information_schema.statistics "
                + "WHERE table_schema = ? AND table_name = 'outbox_events' AND index_name = ? "
                + "ORDER BY seq_in_index", (result, rowNum) -> result.getString(1), schema, indexName);
    }

    /** FK 보존 여부를 조회한다. */
    private boolean foreignKeyExists(String constraintName) {
        return db.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE table_schema = ? AND table_name = 'notifications' "
                + "AND constraint_type = 'FOREIGN KEY' AND constraint_name = ?",
                Integer.class, schema, constraintName) > 0;
    }

    /** 인덱스가 없으면 null, 있으면 UNIQUE 여부를 돌려준다. */
    private Boolean indexIsUnique(String indexName) {
        return db.query("SELECT DISTINCT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = ? AND table_name = 'notifications' AND index_name = ?",
                result -> result.next() ? result.getInt(1) == 0 : null, schema, indexName);
    }
}
