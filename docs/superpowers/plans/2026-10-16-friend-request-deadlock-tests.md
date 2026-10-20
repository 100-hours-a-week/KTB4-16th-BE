# 친구 요청 데드락 테스트 진행 계획

> **For agentic workers:** 구현 단계에서는 `superpowers:subagent-driven-development` 또는 `superpowers:executing-plans`를 사용한다. 아래 체크박스는 테스트 진행 상태를 기록한다.

**Goal:** 동시 친구 요청과 알림 처리의 잠금 순환을 재현 가능하게 검증하고, 수정 후 친구 상태·알림·Outbox 결과와 CI 통과를 확인한다.

**Architecture:** 볼륨 없는 별도 MySQL 8.4 DB에서 원본 서비스와 Outbox 경로를 실행한다. 잠금 순서를 고정한 회귀 검사와 실제 요청 결과 검사를 분리하고, 마지막에 전체 테스트와 CI 제외 설정을 확인한다.

**Tech Stack:** Java 21, Spring Boot 4.1, JUnit 5, Gradle, MySQL 8.4, Flyway.

**Spec:** 현재 계약은 `src/test/java/com/ktb4/team16/mulo/friend/service/FriendMysqlIntegrationTest.java:59-95`와 `src/test/java/com/ktb4/team16/mulo/notification/NotificationMysqlIntegrationTest.java:82-105`에 있다. 잠금 경로는 `FriendCommandTransactionalService.java:45-57` 및 `NotificationOutboxRepository.java:90-125`를 기준으로 한다.

## 실행 상태 — 2026-10-16

재현 검사에서 실제 서비스의 데드락을 두 번 확인했다. 기존 친구·알림 통합 테스트 10개는 통과했다. 제품 수정·수정 후 전체 검증·CI 복구는 남아 있다. [상세 실행 결과](../results/2026-10-16-friend-deadlock-progress.md)를 참고한다.

## Global Constraints

- 원본 `mulo` DB와 운영·공용 DB에는 테스트 데이터를 넣지 않는다. 일회용 DB만 사용하고, 테스트 종료 후 로그를 수집한 다음 삭제한다.
- `.env`나 DB 비밀번호를 로그·문서·Git에 기록하지 않는다. `application.yaml:5-10`의 `DB_URL/DB_USERNAME/DB_PASSWORD`를 일회용 DB로 덮어쓴다.
- 기존 Flyway 파일, 공개 API, 친구 성립 규칙은 테스트 편의를 위해 변경하지 않는다.
- 단순히 동시성 테스트를 여러 번 통과한 사실을 데드락 제거의 증거로 삼지 않는다. 잠금 순서 검사와 최종 DB 상태 검사를 모두 통과해야 한다.
- 재시도를 구현한다면 데드락으로 롤백된 **전체 트랜잭션**을 새 트랜잭션에서 다시 실행한다. 알림 dispatcher는 성공 후 한 번만 호출한다.
- 새 테스트 메서드와 테스트 전용 잠금 도우미에는 각각의 책임을 한 줄 주석으로 남긴다.

## Review Focus

1. **역방향 동시 요청:** 결과는 `Pending` 한 건과 `BecameFriends` 한 건, DB에는 pending 0건·friendship 1건이어야 한다.
2. **동일 방향 동시 요청:** 결과는 `Pending` 한 건과 `FRIEND_REQUEST_ALREADY_EXISTS` 한 건, DB에는 pending 1건·friendship 0건이어야 한다.
3. **알림 무결성:** 자동 친구 성립 시 `FRIEND_ACCEPTED` 알림은 수신자별 1건씩 총 2건이며 같은 `source_event_id`를 갖고 해당 Outbox는 `COMPLETED`여야 한다.
4. **재시도 경계:** 확인된 데드락만 제한 재시도하고, 도메인 오류나 다른 DB 오류를 무분별하게 재시도하지 않는다. 실패한 시도에서 친구 행·Outbox 행·알림이 남지 않아야 한다.
5. **다른 경쟁 경로:** 명시 수락과 요청 취소가 겹쳐도 pending과 friendship이 동시에 남지 않아야 한다.

## 현재 확보한 기준 자료

- 2026-10-16, MySQL 8.4.11의 일회용 스키마에서 기존 역방향·동일 방향 테스트는 한 차례씩 통과했다. `FriendMysqlIntegrationTest.runTogether()`는 시작 신호만 맞추므로 이 결과만으로 문제 해소를 주장할 수 없다.
- 같은 스키마에서 SQL 두 세션으로 실제 코드의 잠금 순서를 강제로 겹치자 `ERROR 1213 (40001)`이 발생했다. InnoDB 기록은 알림 트랜잭션이 `friend_requests` 공유 잠금을 가진 채 `users` 공유 잠금을 기다리고, 친구 명령이 `users` 배타 잠금을 가진 채 `friend_requests` 배타 잠금을 기다렸음을 보였다. 재현 스키마는 삭제했다.
- 사용자에게서 당시 사고 로그는 아직 받지 못했다. 위 기록은 **재현된 가능 경로**의 증거이며 당시 사고의 동일 원인을 확정하는 자료는 아니다.
- 로컬 `mulo` 계정은 `performance_schema.data_lock_waits` 조회 권한이 없다. 잠금 관찰은 일회용 DB의 관리자 계정으로 수행하고 일반 개발 DB 계정 권한은 바꾸지 않는다.

## Task 1: 격리된 실행 환경과 진단 자료

**Files:** 코드 변경 없음. `README_DEV_SETUP.md:109-125`, `src/main/resources/application.yaml:5-29`, `.github/workflows/ci-dev.yml:68-86`을 환경 기준으로 사용한다.

- [x] Java 21과 MySQL 8.4를 확인하고 로컬 3308 포트가 비어 있는지 확인한다.
- [x] `.env`를 셸에 로드한 뒤 볼륨 없는 `mysql:8.4` 일회용 컨테이너 `mulo-friend-deadlock-it`와 스키마 `mulo_friend_deadlock_it`을 만든다. `innodb_print_all_deadlocks=ON`을 설정한다. Docker 컨테이너 이름이나 포트가 이미 사용 중이면 기존 자원을 덮어쓰지 않고 새 이름·포트를 정한다.
- [x] `DB_URL`을 `jdbc:mysql://127.0.0.1:3308/mulo_friend_deadlock_it?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul`로 지정하고 `DB_USERNAME/DB_PASSWORD`는 일회용 DB 계정으로 지정한다. `SPRING_PROFILES_ACTIVE`는 비워 기본 설정을 사용한다. `MULO_WEATHER_IT_DB_*`는 이 테스트의 DB 선택에 영향을 주지 않는다.
- [x] 실패 시 JUnit XML (`build/test-results/test`), Gradle 테스트 리포트 (`build/reports/tests/test`), MySQL 오류 로그와 `SHOW ENGINE INNODB STATUS`를 보존한다. `LATEST DETECTED DEADLOCK`은 최근 한 건만 남으므로 모든 데드락은 오류 로그도 확인한다.
- [x] 모든 세션 종료와 로그 수집을 확인한 뒤 **이 작업에서 만든 컨테이너만** 중지한다. `--rm`으로 일회용 DB가 삭제되는지 확인한다.

## Task 2: 잠금 순서를 고정한 회귀 검사

**Files:** 새 테스트는 `src/test/java/com/ktb4/team16/mulo/friend/service/FriendDeadlockMysqlIntegrationTest.java`에 둔다. 관찰 연결은 테스트 전용 환경변수 `MULO_DEADLOCK_IT_ADMIN_URL`, `MULO_DEADLOCK_IT_ADMIN_USERNAME`, `MULO_DEADLOCK_IT_ADMIN_PASSWORD`로 구성하며, CI에서는 일회용 MySQL의 관리자 계정을 이 세 변수에만 전달한다. 일반 개발 DB의 `mulo` 계정 권한은 바꾸지 않는다.

| 현재 검사 | 추가할 검사 |
| --- | --- |
| `FriendMysqlIntegrationTest.java:168-180`의 시작 `CountDownLatch` | 두 사용자 X 잠금 획득과 Outbox의 사용자 S 잠금 대기를 각각 관찰한 뒤 요청을 진행하는 잠금 단계 동기화 |

- [ ] 첫 요청을 `FriendCommandTransactionalService.sendFriendRequest(A.id(), B.nickname())`로 커밋해 pending 요청과 Outbox 이벤트를 준비한다. facade를 거치지 않아 즉시 알림이 먼저 처리되지 않게 한다.
- [ ] 이 테스트에서는 `NotificationOutboxScheduler`를 테스트 전용 mock으로 대체해 예약 worker가 준비한 이벤트를 먼저 집지 못하게 한다.
- [ ] 트랜잭션 B가 `users(A, B)`를 `FOR UPDATE`로 잡은 뒤 latch에서 대기하도록 한다. B는 같은 외부 트랜잭션 안에서 역방향 요청을 호출하게 해 잠금을 유지한다. 별도 스레드에서 실제 `NotificationOutboxProcessor.process(eventId)`를 시작한다.
- [ ] 두 DB 세션의 `CONNECTION_ID()`를 기록하고 관리자 관찰 연결의 `performance_schema.data_lock_waits`와 `data_locks`로 알림 처리의 `users` 잠금 대기를 확인한다. 수정 전에는 알림 트랜잭션이 요청 행의 S 잠금도 보유함을 확인해 같은 순환을 재현한다. 잠금 순서를 수정한 뒤에는 사용자 잠금을 기다리는 동안 요청 행의 S 잠금을 보유하지 않아야 한다.
- [ ] latch를 풀어 B의 역방향 요청 `sendFriendRequest(B.id(), A.nickname())`을 진행한다. B가 반환한 수락 이벤트도 명시적으로 처리한다. 수정 후 합격 조건은 각 Future가 15초 안에 완료, 친구 요청에 노출된 `1213/40001` 없음, pending 0건·friendship 1건, 두 Outbox 이벤트 `COMPLETED`, Outbox 실패 기록 없음이다. `NotificationOutboxProcessor.process()`는 내부 실패를 기록하고 정상 반환할 수 있으므로 Future 완료만으로 합격 처리하지 않는다. 이 검사는 스레드 시작 시각에만 의존하지 않아야 한다.
- [ ] 수정 전 SQL 순서만 재생하는 별도 스크립트는 원인 설명 자료로만 사용한다. 수정 후에도 그 SQL 자체는 데드락을 만들 수 있으므로 이를 제품 코드의 회귀 합격 기준으로 사용하지 않는다.

## Task 3: 결과·재시도·알림 검증

**Files:** `src/test/java/com/ktb4/team16/mulo/friend/service/FriendMysqlIntegrationTest.java:59-131`, `src/test/java/com/ktb4/team16/mulo/notification/NotificationMysqlIntegrationTest.java:82-105`. 재시도를 적용한다면 facade 단위 테스트를 `src/test/java/com/ktb4/team16/mulo/friend/service/FriendCommandRetryTest.java`에 추가한다.

- [x] 기존 `concurrentOppositeRequestsBecomeOneFriendship`와 `concurrentSameDirectionRequestsKeepOnePending`을 둘 다 실행하고 Review Focus 1·2의 반환값과 DB 수를 확인한다.
- [x] 기존 `concurrentAcceptAndDeleteLeaveConsistentState`를 실행해 Review Focus 5를 확인한다.
- [x] `mutualFriendRequestsCreateTwoNotificationsForOneCompletedEvent`를 실행해 `FRIEND_ACCEPTED` 2건, `source_event_id` 한 개, Outbox `COMPLETED`를 확인한다. 같은 사용자·이벤트에 중복 알림이 없는지도 확인한다.
- [ ] 재시도가 구현되었다면 worker의 첫 호출만 데드락으로 실패시키고 다음 호출이 성공하는 단위 테스트를 추가한다. 성공한 worker 결과에 대해서만 dispatcher가 1회 호출되는지 확인한다. 재시도 소진 및 도메인 오류 비재시도도 확인한다.
- [ ] 잠금 순서 수정이 Outbox 공통 경로에 들어갔다면 월간 리포트 알림 등 다른 `NotificationMysqlIntegrationTest`도 실행한다.

## Task 4: 검증 실행과 CI 복구

**Files:** `.github/workflows/ci-dev.yml:88-101,246-259`, `.github/workflows/ci-prod.yml:88-101,246-259`.

- [ ] 일회용 DB에서 먼저 새 잠금 회귀 테스트와 아래 두 기존 동시성 테스트를 단독 실행한다. 실패하면 전체 테스트로 확대하기 전에 InnoDB 대기 그래프와 SQLState를 조사한다.

```bash
./gradlew test \
  --tests 'com.ktb4.team16.mulo.friend.service.FriendDeadlockMysqlIntegrationTest' \
  --tests 'com.ktb4.team16.mulo.friend.service.FriendMysqlIntegrationTest.concurrentOppositeRequestsBecomeOneFriendship' \
  --tests 'com.ktb4.team16.mulo.friend.service.FriendMysqlIntegrationTest.concurrentSameDirectionRequestsKeepOnePending' \
  --rerun-tasks
```

- [ ] 관련 친구·알림 테스트를 실행하고, 마지막에 `./gradlew clean build jacocoTestCoverageVerification`를 실행한다. 실제 실행 결과와 실패 수를 기록한다.
- [ ] 위 테스트가 통과한 뒤 CI dev/prod의 네 곳에서 두 테스트를 제외하는 임시 init script와 `--init-script` 사용을 제거한다.
- [ ] CI의 일회용 MySQL 서비스에서만 사용할 관리자 관찰 연결을 테스트 단계 환경변수 `MULO_DEADLOCK_IT_ADMIN_*`에 추가한다. 현재 워크플로의 테스트용 `root` 계정을 사용하고, 일반 `mulo` 계정에 전역 잠금 관찰 권한을 부여하지 않는다. 관리자 연결값은 Gradle 출력에 인쇄하지 않는다.

| 현재 CI | 수정 후 CI |
| --- | --- |
| `./gradlew --init-script "$RUNNER_TEMP/exclude-deadlock-tests.gradle" test jacocoTestReport jacocoTestCoverageVerification` | `./gradlew test jacocoTestReport jacocoTestCoverageVerification` |

- [ ] CI에서도 제외 없이 두 동시성 테스트·전체 테스트·JaCoCo 검증이 통과해야 완료로 판정한다. 반복 실행은 타이밍 회귀를 찾는 보조 수단이며 Task 2의 잠금 순서 검사를 대체하지 않는다.

## 중단·실패 기준

- 일회용 DB가 아닌 `mulo` 또는 공용 DB를 가리키면 테스트를 시작하지 않는다.
- `1213/40001`이 다시 발생하거나 테스트가 시간 초과되면 해당 실행은 실패다. 결과 행 수가 우연히 맞아도 통과로 처리하지 않는다.
- 알림이 `PENDING`/`FAILED`에 남거나 같은 수신자·이벤트에 중복 생성되면 실패다.
- 실제 사고 로그가 나중에 제공되면 이 계획의 재현 잠금 그래프와 비교하고, 다른 그래프라면 해당 경로의 검사를 추가한다.

## 근거

- MySQL 8.4: [데드락 진단·잠금 순서·트랜잭션 재시도](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html)
- MySQL 8.4: [외래 키 검사에 따른 공유 레코드 잠금](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)
- MySQL 8.4: [데드락 시 전체 트랜잭션 롤백](https://dev.mysql.com/doc/refman/8.4/en/innodb-error-handling.html)
- MySQL 8.4: [`data_locks`](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-data-locks-table.html), [`data_lock_waits`](https://dev.mysql.com/doc/refman/8.4/en/performance-schema-data-lock-waits-table.html)
