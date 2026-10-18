# 친구 요청 알림 조건부 INSERT 변경 및 검증

## 범위

2026-10-18 사용자가 승인한 조건부 INSERT만 구현한다. 친구 요청 알림에서 원래 요청 PK 확인과 저장을 한 SQL로 합친다. 사용자 선잠금, 현재 읽기, 종료 요청 생략, Outbox 완료·재시도·Savepoint를 유지한다. 기존 알림 자동삭제·지연 처리·이벤트 무효화는 포함하지 않는다.

현재 상태: 구현·집중 검증·두 결정적 잠금 회귀·전체 검증·독립 코드 리뷰 완료.

## 기존 코드와 변경 코드

대상: [NotificationOutboxRepository.java:120](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/main/java/com/ktb4/team16/mulo/notification/outbox/NotificationOutboxRepository.java:120).

### 기존

```java
Long targetId = lockExistingTarget(draft.type(), draft.targetId());
if (draft.type() == NotificationType.FRIEND_REQUEST && targetId == null) {
    return;
}
// 요청이 있으면 별도의 INSERT VALUES를 실행한다.
jdbcTemplate.update(/* INSERT INTO notifications ... VALUES ... */, ...);
```

```sql
SELECT friend_request_id FROM friend_requests
WHERE friend_request_id = ? FOR SHARE;
-- 존재하면 별도 문장으로 INSERT INTO notifications ... VALUES ...;
```

### 변경

```java
if (draft.type() == NotificationType.FRIEND_REQUEST) {
    insertPendingFriendRequestNotification(eventId, draft);
    return;
}
// 다른 유형은 기존 대상 조회 및 INSERT VALUES 경로로 처리한다.
```

[전용 메소드:140](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/main/java/com/ktb4/team16/mulo/notification/outbox/NotificationOutboxRepository.java:140)의 실제 SQL:

```sql
INSERT INTO notifications (
    user_id, type, friend_request_id, source_event_id, title, content
)
SELECT ?, 'FRIEND_REQUEST', request.friend_request_id, ?, ?, ?
FROM friend_requests request
WHERE request.friend_request_id = ?
FOR SHARE;
```

| 상황 | 기존 | 변경 |
| --- | --- | --- |
| 정상 pending 요청 | 대상 SELECT 1 + INSERT 1 | 조건부 INSERT 1 |
| 이미 종료된 요청 | SELECT 1, 생성 0행 | 조건부 INSERT 1, 생성 0행 |
| 친구 상태 추가 조회 | 0회 | 0회 |
| 원래 요청 세대 | requestId로 확인 | 같은 requestId로 확인 |
| 수락·리포트 등의 대상 NULL 이력 | 유지 | 유지 |

측정 범위는 **대상 확인·저장 SQL 실행 수**다. DB 내부 PK 검색은 남는다. worker의 이벤트 잠금·사용자 잠금·완료 UPDATE 및 JDBC 준비 비용을 포함한 전체 네트워크 왕복수나 응답 시간은 측정하지 않았다.

## 메소드별 책임과 핵심 주석

- `insertNotification`: 알림 유형에 따라 조건부 생성 또는 기존 저장 경로를 선택한다.
- `insertPendingFriendRequestNotification`: 원래 요청 PK를 현재 읽기로 확인하면서 해당 요청 알림만 저장한다. 요청 소실 시 INSERT 0행은 정상이다.
- `NotificationOutboxProcessor.processLocked`: 기존 역할대로 수신자 잠금 → 알림 저장 → 이벤트 COMPLETED를 같은 독립 트랜잭션·Savepoint 안에서 수행한다. 이 파일은 이번에 변경하지 않았다.

신규 메소드와 SQL 앞에 책임·사용자 선잠금 전제·FOR SHARE·0행 정상 완료를 주석으로 남겼다.

## 잠금과 현재 읽기

```text
친구 명령: 사용자 low/high X → 친구 요청/관계 상태 변경 → COMMIT
알림 worker: 이벤트 X → 수신자 사용자 S → 조건부 INSERT(요청 S, 알림 INSERT) → COMMIT
```

SQL을 합쳐도 worker의 사용자 선잠금을 제거하지 않는다. 명령이 사용자를 먼저 잠그면 worker는 요청에 접근하기 전에 대기한다. worker가 먼저 사용자를 잠그면 명령은 요청을 변경하기 전에 대기한다. 한쪽이 상대의 요청 잠금을 기다리면서 반대쪽이 사용자 잠금을 기다리던 기존 순환을 재도입하지 않는다.

`FOR SHARE`는 RC/RR에서 최신 요청을 읽고 트랜잭션 종료까지 행을 보호하기 위해 명시한다. 특히 RR의 오래된 일반 SELECT snapshot에 삭제된 요청이 보이더라도, 조건부 INSERT가 그 과거 행으로 알림을 생성해서는 안 된다. 같은 사용자 쌍에 새 요청이 생겨도 원래 요청 PK를 사용하므로 이전 이벤트는 생략된다.

근거: [MySQL INSERT SELECT](https://dev.mysql.com/doc/refman/8.4/en/insert-select.html), [잠금 읽기](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [SQL별 잠금·FK 검사](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html).

## 검증 기록

환경: Java 21, 기존 DB와 분리한 일회용 MySQL 8.4.11, 127.0.0.1:3307. 서버 기본 격리는 REPEATABLE-READ, `innodb_autoinc_lock_mode=2`이며 새 Repository 테스트는 RC/RR 각각 실행했다. 기존 `.env`나 공용 DB 대신 테스트용 임의 자격 증명·격리 스키마를 사용한다. 실제 외부 AI/채팅 요청을 보내지 않는 테스트 환경이다.

- RED: 새 SQL 실행 수 테스트가 기존 구현에서 RC/RR 모두 예상 1, 실제 2로 실패했다. 과거 snapshot·취소 후 재요청 보존 테스트 2개는 기존 코드에서 통과했다.
- GREEN 집중 검증: SQL·snapshot 4개 + 기존 알림 8개 + 친구 34개(역방향 동시 요청 30회 포함), 총 46개 통과. InnoDB `lock_deadlocks`는 enabled 상태에서 0 → 0.
- 기존 명령 선행 결정적 테스트: 실제 사용자 잠금 대기 관찰, pending 0, 친구 1, 성립 알림 2, 늦은 요청 알림 0, 두 Outbox COMPLETED/retry 0. 데드락 계수 0 → 0.
- 최초 테스트 준비 중 fixture 닉네임이 스키마 VARCHAR(10)를 초과해 실패했다. fixture 길이만 수정한 뒤 위 RED를 별도로 확인했다. 이 준비 실패는 제품 코드 실패 또는 RED 증거로 계산하지 않았다.

### 두 잠금 순서를 강제로 만든 검증

[FriendDeadlockMysqlIntegrationTest.java:86](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/test/java/com/ktb4/team16/mulo/friend/service/FriendDeadlockMysqlIntegrationTest.java:86)와 [worker 선행 테스트:177](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/test/java/com/ktb4/team16/mulo/friend/service/FriendDeadlockMysqlIntegrationTest.java:177)를 실제 실행했다. 2개 모두 통과, skip 0, InnoDB 데드락 계수 0 → 0.

| 강제 순서 | 실제 관찰 | 최종 결과 |
| --- | --- | --- |
| 역방향 명령이 사용자 X 선점 | worker는 사용자 S에서 대기하며 요청 S를 보유하지 않음 | 친구 1, pending 0, 성립 알림 2, 늦은 요청 알림 0 |
| worker가 사용자 S 선점 | 요청 X barrier에서 worker의 요청 S 대기 + 사용자 S 보유 확인, 이어 역방향 명령의 사용자 X 대기 확인 | 친구 1, pending 0, 성립 알림 2, 먼저 생성된 요청 알림 이력 1 |

두 경우 모두 요청·성립 이벤트가 COMPLETED이고 retry_count=0, last_error_code=NULL이다. 두 번째 사례의 외부 연결 요청 X 잠금은 순서를 강제하기 위한 테스트 barrier이며, 실제 명령·worker·Repository를 대체하지 않는다. barrier만 해제한 뒤 worker 커밋이 역방향 명령을 진행시킨다.

### SQL 및 요청 세대 검증

[NotificationConditionalInsertMysqlIntegrationTest.java:65](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/test/java/com/ktb4/team16/mulo/notification/outbox/NotificationConditionalInsertMysqlIntegrationTest.java:65)는 동일 트랜잭션 연결의 MySQL Com_select/Com_insert/Com_insert_select 계수 합으로 대상 확인·저장 문장 1회를 확인한다. SHOW 계수 조회 자체는 이 합에 포함되지 않는다. [90행](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/test/java/com/ktb4/team16/mulo/notification/outbox/NotificationConditionalInsertMysqlIntegrationTest.java:90)은 과거 snapshot 생성 → 다른 트랜잭션 취소·재요청 → 원래 이벤트 0건·새 이벤트 1건을 RC/RR에서 각각 확인한다.

[FriendMysqlIntegrationTest.java:67](/Users/bohyeon/Documents/KTB/Team16/app/mulo-be/src/test/java/com/ktb4/team16/mulo/friend/service/FriendMysqlIntegrationTest.java:67)는 두 실제 요청 작업자가 준비된 뒤 같은 신호로 출발하도록 하고 30회 반복한다. 각 회차의 Pending/BecameFriends 결과, 친구 수, Outbox 완료·retry 0·error NULL, 수락 알림 수신자 두 명을 확인한다.

### 최종 전체 검증

```bash
./gradlew --no-daemon checkstyleMain test jacocoTestCoverageVerification --rerun-tasks
```

- 종료 코드 0, BUILD SUCCESSFUL.
- 총 790개: 통과 787, 실패 0, 오류 0, skip 3.
- 관련 실제 실행: FriendMysqlIntegrationTest 34개(역방향 동시 요청 30회 포함), NotificationMysqlIntegrationTest 8개, 조건부 INSERT 4개, 결정적 데드락 회귀 2개 모두 통과.
- 별도 migration 테스트용 `MULO_NOTIFICATION_IT_DB_URL/USERNAME/PASSWORD`를 설정하지 않아 기존 NotificationMigrationMysqlIntegrationTest의 스키마 생성·이력 보존·AI 이력 중단 검사 3개가 assumption으로 skip됐다. 이번에는 migration을 변경하지 않았다.
- Checkstyle 및 LINE 커버리지 게이트 통과. LINE 커버리지 87.37%(covered 3465, missed 501), 기준 60%.
- 전체 실행 동안 `information_schema.innodb_metrics`의 enabled `lock_deadlocks`: 0 → 0.
- 독립 리뷰에서 수정할 구체적 결함이 발견되지 않았다. 최종 추가한 worker 선행 테스트도 별도로 리뷰했다.
- 기존 deprecated/unchecked 컴파일 안내와 JVM class sharing 안내는 출력됐다. 검증 실패는 아니다.

[전체 실행 메타데이터](/tmp/mulo-conditional-insert-20261018/full-summary.json), [전체 로그](/tmp/mulo-conditional-insert-20261018/full.log), [집중 검증](/tmp/mulo-conditional-insert-20261018/focused-summary.json), [결정적 잠금 검증](/tmp/mulo-conditional-insert-20261018/deadlock-summary.json).

검증 후 일회용 테스트 컨테이너·익명 볼륨·임의 자격 증명 파일을 제거했다. 기존 3306 MySQL과 다른 컨테이너는 변경하지 않았다.

원시 로그·XML·실행 명령·DB 지표: `/tmp/mulo-conditional-insert-20261018/`.

## 한계와 변경 범위

- 실행한 순서와 환경에서의 검증 결과이며 모든 운영 트래픽·DB 설정에서 데드락이 불가능하다는 증명은 아니다.
- 이미 생성된 요청 알림은 기존 정책대로 보존된다. 이번 변경은 늦은 신규 생성의 생략 정책을 유지한다.
- DB 스키마·Flyway·API·의존성·트랜잭션 경계는 변경하지 않는다.
- 기존 AI 및 명세·프론트 미커밋 작업은 이번 변경에 포함해 수정하지 않는다. 전체 테스트는 현재 작업 폴더의 코드로 실행한다.
- 이 요청으로 커밋·푸시·PR 갱신은 수행하지 않는다.


## PR 업로드 후보 재검증 — 2026-10-18

사용자가 기존 요청 알림 이력을 유지하는 현재 상태의 PR 업로드를 승인했다. 최신 `origin/develop`의 `a6b97e7`(CI/CD 개선)을 작업 브랜치에 충돌 없이 병합했다. 공유 명세는 친구 관련 hunk만 선택하고, AI 임시 응답 제거·프론트 종료 배지·과거 stale 계획/결과는 업로드 후보에서 제외했다.

Git index를 `/tmp/mulo-friend-pr-update-20261018/candidate/`에 추출하여 **실제 업로드 후보만** 일회용 MySQL 8.4.11에서 재검증했다. 명령은 `./gradlew --no-daemon checkstyleMain test jacocoTestCoverageVerification --rerun-tasks`이며 종료 코드 0, 총 788개 중 785개 통과·3개 skip·실패/오류 0이다. 앞선 790개는 AI 미커밋 변경을 포함한 작업 폴더의 결과이고, 이 788개가 PR 후보의 결과다.

- skip 3개는 별도 알림 migration 관리자 환경변수 미설정에 따른 기존 assumption이다.
- 두 결정적 잠금 회귀 2개, 친구 통합 34개(역방향 동시 요청 30회 포함), 알림 통합 8개, 조건부 INSERT RC/RR 4개가 실제 실행되어 모두 통과했다.
- InnoDB enabled `lock_deadlocks`: 0 → 0. 서버 기본 REPEATABLE-READ, `innodb_autoinc_lock_mode=2`.
- Checkstyle 통과, LINE 커버리지 87.35%, 기준 60% 통과.
- JSON/Swagger 내장 명세 일치와 친구 관련 4개 operation의 YAML/JSON 설명 일치를 확인했다. 병합된 dev/prod CI는 각 2개의 데드락 회귀 단계를 유지한다.
- Git 미해결 충돌 없음. 이번 PR의 DB/Flyway·공개 API 필드/상태 코드·의존성 변경 없음. 기존 알림 이력 유지와 늦은 신규 생성 생략을 정책·OpenAPI 설명에 명시했다.
- 실제 업로드 승인을 받은 뒤 commit/push하고 기존 PR #124를 갱신한다. 앞선 '이번 요청으로 커밋하지 않음'은 구현 당시의 범위를 기록한 것이며, 이번 후속 승인으로 업로드를 진행한다.

원시 재검증 로그·XML·요약: `/tmp/mulo-friend-pr-update-20261018/`. 일회용 컨테이너 및 자격 증명은 업로드 준비 완료 후 정리한다.
