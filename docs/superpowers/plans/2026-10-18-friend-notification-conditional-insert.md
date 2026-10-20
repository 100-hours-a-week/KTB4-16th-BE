# 친구 요청 알림 조건부 INSERT 구현 계획

> **For agentic workers:** Use superpowers:executing-plans. 사용자가 1번 설계의 구현과 실제 MySQL 동시성 검증을 승인했다.

**Goal:** 친구 요청의 현재 존재 확인과 알림 INSERT를 한 SQL로 합치고 기존 동시성·생략 정책을 보존한다.

**Architecture:** `NotificationOutboxRepository.insertNotification`에서 FRIEND_REQUEST만 전용 조건부 INSERT로 분기한다. 사용자 오름차순 선잠금, 원래 요청 PK, 명시적 FOR SHARE, Savepoint, 0행 정상 완료를 유지한다.

**Tech Stack:** Java 21, Spring JDBC/JPA, MySQL 8.4, JUnit 5.

**Spec:** 사용자의 2026-10-18 조건부 INSERT 승인 및 `specs/MULO_서비스_정책_정의서_v2.1.md`의 NOTI-007/008/014. 프론트 종료 배지 표시는 이번 백엔드 PR의 범위에 포함하지 않는다.

## Global Constraints

- DB 스키마·API·의존성·트랜잭션 경계 변경 없음.
- 다른 알림 유형의 대상 NULL 이력 보존을 유지한다.
- 기존 AI·프론트 및 다른 미커밋 변경을 보존한다. 이번 요청에 커밋·푸시는 포함하지 않는다.
- 기존 작업 브랜치 `fix/friend-notification-deadlock-248`에서 계속한다.

## Review Focus

- 정상 요청의 대상 확인·저장은 SQL 실행 1회여야 한다.
- RC/RR 모두 종료된 요청을 생성하지 않아야 한다.
- RR의 오래된 snapshot과 취소 후 새 요청이 옛 이벤트를 살리지 않아야 한다.
- 사용자 선잠금 이후 요청 잠금이라는 기존 순서를 보존한다.
- worker가 오류를 삼키더라도 Outbox 상태·재시도와 InnoDB 데드락 지표로 실패를 탐지한다.

## Task 1: SQL 통합 및 회귀 검증

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/notification/outbox/NotificationOutboxRepository.java`
- Create: `src/test/java/com/ktb4/team16/mulo/notification/outbox/NotificationConditionalInsertMysqlIntegrationTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/friend/service/FriendMysqlIntegrationTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/friend/service/FriendDeadlockMysqlIntegrationTest.java`
- Create: `docs/superpowers/results/2026-10-18-friend-notification-conditional-insert.md`

**Interface:** `void insertNotification(UUID eventId, NotificationDraft draft)`를 유지한다.

- [x] 일회용 MySQL과 SQL 실행 횟수·RC/RR·오래된 snapshot 테스트를 준비한다.
- [x] 기존 코드에서 정상 요청 SQL 실행 2회 때문에 테스트가 실패하는 것을 확인한다.
- [x] FRIEND_REQUEST만 `INSERT SELECT WHERE 원래 PK = ? FOR SHARE`로 변경하고 책임 주석을 작성한다.
- [x] SQL 테스트, 명령 선행·worker 선행의 결정적 잠금 회귀, 역방향 동시 요청 30회, 기존 친구·알림 통합 테스트를 실행한다.
- [x] 전체 `test`, `checkstyleMain`, 커버리지 게이트를 실행하고 실제 결과를 읽는다.
- [x] 독립 리뷰 후 기존/변경 SQL, 잠금 순서, 실행 결과와 한계를 결과 문서에 기록한다.

**Verification:** Java 21 및 일회용 MySQL에서 `./gradlew --no-daemon test --rerun-tasks`, `checkstyleMain jacocoTestCoverageVerification`. 테스트별 XML·로그·InnoDB 지표를 별도 보관한다. 예상 결과는 실패/오류 0, 결정적 데드락 테스트 실제 실행, 동시 요청 30회 완료이며 기존 환경 조건부 migration 테스트의 skip은 명시한다.

**References:** [MySQL INSERT SELECT](https://dev.mysql.com/doc/refman/8.4/en/insert-select.html), [Locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [InnoDB SQL locks](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html).
