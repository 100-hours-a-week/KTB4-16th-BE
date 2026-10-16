# Friend notification lock order implementation plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement the tasks in order. The existing failing integration test is the red test for Task 1.

**Goal:** Make friend commands and friend notification processing acquire shared user rows before friend request or friendship rows, removing the reproduced deadlock while preserving each event's intended notification result.

**Architecture:** Keep the current command/Outbox transaction boundaries and public API. Inside one Outbox event transaction, collect every friend notification recipient, deduplicate and sort user IDs, acquire each existing user row with `FOR SHARE`, then lock the friend target and insert notifications. This keeps the current target FK protection and puts both paths in user → friend target order. Other notification types keep their existing order until their own lock graph has been reviewed.

**Tech Stack:** Java 21, Spring Boot 4.1.1, JDBC, JUnit 5, MySQL 8.4, Gradle.

**Spec:** `docs/superpowers/results/2026-10-16-friend-deadlock-progress.md`, the user's choice to align friend and friend notification locks in user → friend target order, and the user's decision to show the original request notification even if friendship has already formed.

**Work branch:** `fix/friend-notification-deadlock-248`, created from `origin/develop` in the original backend checkout. The user selected wiki task [#248](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/248).

## Global constraints

- Do not modify Flyway, schema, public API, dependencies, or the command/Outbox transaction boundaries.
- Run MySQL tests against a disposable `mulo_friend_deadlock_it` schema; never write test data to the local `mulo` database.
- Keep the existing Outbox event row locking and target `FOR SHARE` until their distinct roles are evaluated.
- Record responsibilities in concise comments for each newly added method.
- Preserve test logs without environment secrets. Do not mix this change with the unrelated `feat/member-withdrawal-216` commit history.
- Keep the current late request notification behavior: store the request notification with a null request target and complete its Outbox event.

## Review focus

1. A reverse request during processing of the first request notification forms exactly one friendship without `1213/40001`.
2. The Outbox worker waiting on a user row holds no `friend_requests` or `friendships` shared target lock.
3. A two-recipient accepted event locks user IDs in ascending order before a friendship target lock and produces one accepted notification per recipient.
4. A deleted target or recipient keeps the user-approved behavior and does not leave the Outbox stuck.
5. Other event types, especially monthly report, retain their existing notification and rollback behavior.

## Task 1: Freeze the current evidence

**Files:** `docs/superpowers/results/2026-10-16-friend-deadlock-evidence.txt`, `docs/superpowers/results/2026-10-16-friend-deadlock-progress.md`, `src/test/java/com/ktb4/team16/mulo/friend/service/FriendDeadlockMysqlIntegrationTest.java`.

- [x] Verify and record SHA-256 of the 107-line InnoDB trace; check no secret or environment value entered the archived files.
- [x] Keep the original failing JUnit behavior in the test: user lock wait with request S lock, then MySQL 1213 and pending 1/friendship 0.
- [x] Record disposable DB prerequisites and rerun commands in `docs/superpowers/results/2026-10-16-friend-deadlock-fix.md`. Keep generated credentials outside Git.

## Task 2: Lock all friend notification recipients first

**Files:** `src/main/java/com/ktb4/team16/mulo/notification/outbox/NotificationOutboxProcessor.java:65-71`, `src/main/java/com/ktb4/team16/mulo/notification/outbox/NotificationOutboxRepository.java:90-125`, `src/test/java/com/ktb4/team16/mulo/friend/service/FriendDeadlockMysqlIntegrationTest.java`.

**Interface:** Add `NotificationOutboxRepository.lockExistingRecipientsInIdOrder(List<Long> recipientIds)` for friend event processing. Its input contains every draft's recipient ID; it sorts/deduplicates and performs one `SELECT user_id FROM users WHERE user_id = ? FOR SHARE` per ID in the current transaction.

- [x] **RED:** Run the existing `FriendDeadlockMysqlIntegrationTest.oppositeRequestAndNotificationCompleteWithoutLockCycle` with disposable MySQL and administrator lock observation. Observed request S held while waiting for user S, MySQL 1213, assertion failure.
- [x] **GREEN:** In `NotificationOutboxProcessor.processLocked`, build `List<NotificationDraft>` once inside the existing savepoint block. For `FRIEND_REQUEST` and `FRIEND_ACCEPTED`, pass all recipient IDs to the new repository method before the first `insertNotification`. Keep other event types on the old path. The repository method takes locks in sorted ID order and does not change notification insertion behavior.
- [x] Run the same test. Observed user wait without request S, no SQLState 40001, pending 0, friendship 1, both Outbox events completed, two accepted notifications, no Outbox failure code.
- [x] Review missing-recipient path: a missing row is not locked and the same subsequent FK INSERT fails as before; no alternate recipient is selected. No separate deletion race test was added.

## Task 3: Preserve the confirmed late notification policy

**Files:** `NotificationOutboxRepository.java`, friend notification integration tests.

- [x] The user chose to show the original request notification even when the reciprocal request has already formed a friendship.
- [x] Assert that the event is COMPLETED and that the request notification is stored without a `friend_request_id`, as current `lockExistingTarget` does.

## Task 4: Regression and CI

**Files:** `FriendMysqlIntegrationTest.java`, `NotificationMysqlIntegrationTest.java`, `.github/workflows/ci-dev.yml`, `.github/workflows/ci-prod.yml`.

- [x] Run both friend concurrency tests, accept/delete competition, all notification integration tests, and the deterministic deadlock test on disposable MySQL. Record test counts.
- [x] Run `./gradlew clean build jacocoTestCoverageVerification --rerun-tasks` with the test DB. The first port 3308 run failed 26 unrelated fixed-port checks; the corrected port 3307 run passed.
- [x] In both dev/prod workflows, provision a dedicated disposable `mulo_friend_deadlock_it` schema with access for test user and admin observer, and configure the deterministic test with that schema and the three `MULO_DEADLOCK_IT_ADMIN_*` variables. Actual GitHub Actions run remains pending until push/PR.
- [x] Remove the four temporary CI init-script exclusions for the existing friend concurrency tests. Local full build and JaCoCo gate pass with exclusions removed.

## Task 5: Review and handoff

- [x] Review lock order against friend send, accept, request delete, friendship delete, and the other Outbox event types. The friend command methods use the same active-pair user lock; other event types retain existing code.
- [x] Show the current and changed code sequence in the result report and explain shared vs exclusive row locks and implicit FK locking.
- [x] Archive final JUnit summary and an updated lock observation. Keep the initial red evidence for before/after comparison.
- [x] Create `fix/friend-notification-deadlock-248` from the current `origin/develop` in the original backend checkout.
- [x] Commit the evidence, plan, test, and fix on that branch. No push or PR without a separate request.

## Evidence basis

- Current friend service: `FriendCommandTransactionalService.java:45-57` takes user X before friend target X.
- Current friend notification: `NotificationOutboxRepository.java:90-125` takes target S before INSERT, whose user FK needs S.
- [MySQL 8.4 foreign-key shared row locks](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html).
- [MySQL 8.4 consistent lock order and whole transaction retry](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html).
