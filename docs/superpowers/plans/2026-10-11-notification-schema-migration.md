# Notification Schema Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the existing `notifications` table for four V2 event types and event-level idempotency without losing existing non-AI notifications.

**Architecture:** Apply a new Flyway V7 migration after V6; never edit applied migrations. Fail before removing the playlist schema if an `AI_PLAYLIST` row exists. Keep the friendship FK while replacing its single-column unique index with a regular index, and add nullable event identity with a recipient-level unique key.

**Tech Stack:** MySQL 8.4, Flyway, Java 21, Spring Boot 4.1.1, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-11-notification-schema-migration-design.md`

## Global Constraints

- Work on `feat/notification-207`; do not change shared V1~V6 migrations or develop directly.
- Current `origin/develop` migration head is V6; recheck before creating V7. If another version has landed, stop and revise this plan's file name and version.
- Before applying V7 in each target DB, verify that its `AI_PLAYLIST` row count is zero. Local `mulo` was verified at zero; CI and deployed DBs were not accessible here. V7 must refuse to remove the playlist schema if that check is missed and rows exist.
- Keep `fk_notifications_friendship` with `ON DELETE SET NULL`; keep `chat_room_id` as a logical ID without a Backend FK after V6.
- Keep `source_event_id CHAR(36) NULL` without an Outbox FK, and add `UNIQUE(source_event_id, user_id)`.
- Do not add a dependency, change a public API shape, delete other domain data, or weaken `ddl-auto: validate`.
- New Java test methods need short comments describing their responsibility, per the workspace AGENTS.md instruction.

## Review Focus

- A target DB contains one `AI_PLAYLIST` row: V7 must stop before dropping the playlist FK, column, or enum value. Task 1 tests this.
- Existing non-AI notifications have `source_event_id = NULL`: V7 must keep those rows and allow multiple NULL values. Task 1 tests this.
- Two users receive the same `FRIEND_ACCEPTED` event: both rows must reference the same friendship, while duplicate `(source_event_id, user_id)` fails. Task 1 tests this.
- A nonexistent friendship is used as a target: the retained FK must reject it. Task 1 tests this.
- A prior DDL succeeds but a later DDL fails: the rollout runbook must require schema and Flyway history inspection before retrying, because MySQL DDL commits implicitly. Task 3 covers this.

---

### Task 1: Flyway V7 and MySQL constraint verification

**Files:**
- Create: `src/main/resources/db/migration/V7__prepare_notification_outbox_schema.sql`
- Create: `src/test/java/com/ktb4/team16/mulo/notification/NotificationMigrationMysqlIntegrationTest.java`

**Interfaces:**
- Consumes: V6 schema and `MYSQL_ROOT_PASSWORD` or a separate local test DB credential supplied through `MULO_NOTIFICATION_IT_DB_URL`, `MULO_NOTIFICATION_IT_DB_USERNAME`, `MULO_NOTIFICATION_IT_DB_PASSWORD`.
- Produces: four-value `notifications.type`; nullable `source_event_id`; `uk_notifications_source_event_user`; nonunique `idx_notifications_friendship`; retained `fk_notifications_friendship`.

- [ ] **Step 1: Write a failing MySQL migration test.** `NotificationMigrationMysqlIntegrationTest` creates a uniquely named disposable schema, migrates it only through V6, then tests both branches: with an `AI_PLAYLIST` row V7 fails and the row/column remain; without one V7 succeeds and existing non-AI rows remain. Add assertions for two recipients on one friendship, duplicate event-recipient rejection, nullable historical event IDs, and invalid friendship FK rejection. Each test creates and drops only its own disposable schema, using credentials with schema-create/drop permission for this test only. Use JUnit assumptions to skip when the dedicated integration DB credentials are absent.
- [ ] **Step 2: Run the focused test to confirm the red state.** Run `./gradlew test --tests 'com.ktb4.team16.mulo.notification.NotificationMigrationMysqlIntegrationTest'` with the dedicated local DB credentials. Expect failure because V7 does not exist or its target constraints do not yet exist; credential absence is a skip, not a valid red result.
- [ ] **Step 3: Add V7 with explicit DDL order.** First add enforced `chk_notifications_no_ai_playlist CHECK (type <> 'AI_PLAYLIST')` so pre-existing AI rows fail before destructive changes. Add `source_event_id CHAR(36) NULL`, `uk_notifications_source_event_user (source_event_id, user_id)`, and `idx_notifications_friendship (friendship_id)`; drop only `uk_notifications_friendship`, preserving its FK. Drop `fk_notifications_playlist` and `uk_notifications_playlist`, then drop `recommendation_playlist_id`. Finally change `type` to exactly `MONTHLY_REPORT_CREATED`, `MATCH_COMPLETED`, `FRIEND_REQUEST`, `FRIEND_ACCEPTED` and drop the temporary CHECK in the same `ALTER TABLE` statement, as required when changing a CHECK-referenced column. Put a brief purpose comment before each DDL group. Do not modify `chat_room_id`.
- [ ] **Step 4: Run the focused test to confirm green.** Same command as Step 2. Expect all test methods to pass, including the refusal branch and preserved non-AI rows.
- [ ] **Step 5: Commit the migration and test.** `git add src/main/resources/db/migration/V7__prepare_notification_outbox_schema.sql src/test/java/com/ktb4/team16/mulo/notification/NotificationMigrationMysqlIntegrationTest.java` then `git commit -m 'feat: 알림 테이블 V2 스키마 준비 #207'`.

### Task 2: Policy and table-definition synchronization

**Files:**
- Modify: `specs/MULO_서비스_정책_정의서_v2.1.md:312`
- Modify: `specs/MULO_테이블_정의서_v8.1.md:955`
- Inspect: `specs/openapi/MULO_OpenAPI_v2.8.yaml:5275`, same-version JSON and Swagger HTML.

**Interfaces:**
- Consumes: verified V7 schema from Task 1.
- Produces: documents that distinguish the four new event types, describe current FK/index behavior, and avoid claiming `AI_PLAYLIST` remains in the DB.

- [ ] **Step 1: Record the original and revised rules in the policy document.** Change NOTI-001 from five types to the exact four V2 types, state that this is the first notification implementation, and cite the V7 migration. Keep NOTI-002 through NOTI-010 unchanged unless V7 verification identifies a direct contradiction.
- [ ] **Step 2: Align the table definition with V7 and V6.** Remove the `AI_PLAYLIST` type and `recommendation_playlist_id` column/relation; add `source_event_id CHAR(36) NULL`, `uk_notifications_source_event_user`, and nonunique friendship index; change the friendship relation from 0..1:1 to 0..N per friendship; describe `chat_room_id` as a logical Chat DB reference without a Backend FK. Remove the document-only `chk_notifications_type` claim if V7 does not create such a named constraint.
- [ ] **Step 3: Audit the OpenAPI v2.8 trio.** Search YAML, JSON, and Swagger HTML for `AI_PLAYLIST` in notification operations and schemas. Current YAML search found no such enum/example; if the audit remains clear, record no API contract change. If an actual notification contract reference exists, update the three equivalent artifacts together in this task and revalidate their equivalence.
- [ ] **Step 4: Verify the document diff.** Run `git diff --check` and `rg -n 'AI_PLAYLIST|fk_notifications_chat_room|uk_notifications_friendship|source_event_id' specs/MULO_서비스_정책_정의서_v2.1.md specs/MULO_테이블_정의서_v8.1.md specs/openapi/MULO_OpenAPI_v2.8.yaml`; inspect every match in context. Expect only historical/changelog mentions of removed items, if any.
- [ ] **Step 5: Commit the document changes.** Use `git add` on the changed spec files only, then `git commit -m 'docs: 알림 V2 스키마 정책 동기화 #207'`.

### Task 3: Local application and rollout evidence

**Files:**
- Create: `docs/notification-schema-v7-verification.md`
- Modify: none of the existing V1~V6 migrations.

**Interfaces:**
- Consumes: V7 and synchronized documents from Tasks 1–2.
- Produces: recorded local schema checks and exact preflight/rollout checks for each other target DB.

- [ ] **Step 1: Run the read-only local preflight.** Against local `mulo`, record `SELECT COUNT(*) FROM notifications WHERE type = 'AI_PLAYLIST'`, `SELECT type, COUNT(*) FROM notifications GROUP BY type`, `SHOW CREATE TABLE notifications`, `SHOW INDEX FROM notifications`, and latest `flyway_schema_history` version. Abort if `AI_PLAYLIST` count is nonzero, schema differs from V6, or a newer Flyway version appeared.
- [ ] **Step 2: Apply V7 through Spring/Flyway to local `mulo`.** Load the existing ignored local `.env` without printing secrets, then run `./gradlew test --tests 'com.ktb4.team16.mulo.MuloApplicationTests'`. Expect Spring startup, Flyway V7 application, and Hibernate `validate` success. Do not use manual `ALTER TABLE` on `mulo`.
- [ ] **Step 3: Re-read local state.** Assert Flyway V7 success; `AI_PLAYLIST` and playlist column/FK absent; friendship FK and `ON DELETE SET NULL` present; new event-recipient unique and nonunique friendship indexes present; pre-existing non-AI row count unchanged.
- [ ] **Step 4: Run focused and broader checks.** Run the Task 1 integration test with its disposable DB, then `./gradlew test` with required DB environment. Report tests requiring an unavailable dedicated `3307` service separately; do not claim the full suite passed if any fail or skip unexpectedly.
- [ ] **Step 5: Document the rollout gate.** Record the verified local facts and the exact read-only preflight queries for CI/develop and deployment DBs. Those environments use GitHub Secrets (`.github/workflows/CI.yaml:353`, `CD.yml:66`) and were not accessible during planning. Their operators must confirm zero AI rows and V6-compatible schema before V7 deployment. If a migration fails after an implicit DDL commit, stop, compare `SHOW CREATE TABLE` and `flyway_schema_history`, and choose a reviewed recovery migration; do not blindly rerun or edit V7.
- [ ] **Step 6: Commit the verification record.** `git add docs/notification-schema-v7-verification.md` then `git commit -m 'docs: 알림 V7 로컬 검증 결과 기록 #207'`.

## Execution boundary

This plan implements the approved notification-table migration only. Outbox table, publisher, processor, Scheduler, alerting, notification API, and cross-database match completion need separate design and implementation plans from the integrated prompt. The four-event policy remains the input for those plans.
