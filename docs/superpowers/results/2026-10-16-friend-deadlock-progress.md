# 친구 요청 데드락 테스트 결과 — 2026-10-16

실제 서비스와 Repository를 호출하는 통합 테스트로 데드락을 재현했다. 잠금 대기를 고정한 검사는 두 번 모두 같은 경로로 실패했다. 기존 친구·알림 통합 테스트 10개는 통과했다. 제품 코드의 문제는 아직 남아 있다.

## 실행 범위와 환경

- 원본 백엔드: `/Users/bohyeon/Documents/KTB/Team16/app/mulo-be`, 시작 HEAD `4afb901`, 브랜치 `feat/member-withdrawal-216`.
- Java 21, MySQL 8.4.11, 기본 Hikari 풀 3개. SQL은 Flyway로 생성한 실제 스키마에 실행했다.
- 볼륨 없는 일회용 컨테이너 `mulo-friend-deadlock-it-20261016`, 포트 3308, 스키마 `mulo_friend_deadlock_it`을 사용했다.
- 기존 `.env`의 앱 설정을 읽고 DB 연결만 일회용 DB로 덮어썼다. 관리자 관찰 연결은 별도로 사용했다.
- 일반 개발 DB와 기존 컨테이너는 사용하지 않았다. 테스트 사용자·요청·친구 행이 모두 0건임을 확인한 후 이번 컨테이너를 중지했다. `--rm`으로 컨테이너와 일회용 DB가 제거된다.
- Ruling: 앞서 원본 폴더로 통합한 사용자 선호에 따라 이 경로에서 테스트만 추가했다. 브랜치 생성·커밋·push는 수행하지 않았다. 비용/한계: 새 테스트 파일과 문서는 현재 작업 디렉터리의 미커밋 변경으로 남는다.
- Ruling: 이번 실행은 재현과 검증 단계로 수행했다. 회귀 검사 실패를 확인한 뒤 개선안을 제시한다. 제품 수정 후 합격이 필요한 CI 제외 해제는 후속 단계다. 비용/한계: 이번 결과는 수정 완료를 뜻하지 않는다.

## 결과

| 검사 | 결과 | 의미 |
| --- | --- | --- |
| 새 `FriendDeadlockMysqlIntegrationTest.oppositeRequestAndNotificationCompleteWithoutLockCycle` | 실패, 두 번 재현 | 실제 잠금 순환과 MySQL 1213/40001 확인 |
| 기존 `FriendMysqlIntegrationTest` | 5/5 통과 | 역방향·동일 방향 동시 요청, UNIQUE, 수락/취소 경쟁, 친구 페이지 조회 |
| 기존 `NotificationMysqlIntegrationTest` | 5/5 통과 | 수락 알림 2건, 풀 3개 동시 처리, 롤백, 소유권, 월간 알림 |
| 전체 `clean build`·커버리지 게이트 | 미실행 | 새 회귀 검사에서 결함이 확인된 상태. 수정 후 실행 |
| CI 제외 설정 복구 | 미적용 | 새 회귀 검사 합격과 제품 수정 검증 후 적용 |

첫 시도에서는 테스트용 닉네임이 컬럼 길이를 초과했다. 테스트 데이터를 9자로 수정한 뒤 위 두 번의 데드락 재현을 수행했다. 테스트 준비 오류와 실제 데드락 실패를 구분했다.

실행 명령은 다음과 같다. Java 21, `.env`, 일회용 DB 및 관리자 관찰 환경변수는 명령 실행 전에 설정했다.

```bash
./gradlew test \
  --tests com.ktb4.team16.mulo.friend.service.FriendDeadlockMysqlIntegrationTest \
  --rerun-tasks

./gradlew test \
  --tests com.ktb4.team16.mulo.friend.service.FriendMysqlIntegrationTest \
  --tests com.ktb4.team16.mulo.notification.NotificationMysqlIntegrationTest \
  --rerun-tasks
```

새 검사는 `MULO_DEADLOCK_IT_ADMIN_URL`이 설정된 경우에만 실행된다. 기본 테스트/CI에서 이를 설정하지 않으면 새 검사는 건너뛴다. 실행 시 `DB_URL`과 관리자 URL은 같은 로컬 `mulo_friend_deadlock_it` DB를 가리켜야 한다. 이 검사는 잠금 순환 제거를 요구하므로 현재 제품 코드에서는 실패하는 회귀 검사다.

## 재현한 흐름

1. 실제 `FriendCommandTransactionalService.sendFriendRequest(A.id(), B.nickname())`를 커밋해 요청과 Outbox 이벤트를 만든다. facade의 즉시 dispatcher는 호출하지 않는다.
2. 테스트가 역방향 명령 트랜잭션 안에서 두 사용자를 ID 순서대로 `FOR UPDATE`로 잠근다. 이 잠금 단계에서만 대기시킨다.
3. 다른 스레드가 실제 `NotificationOutboxProcessor.process(eventId)`를 호출한다. 예약 scheduler만 mock으로 대체했다.
4. 관리자 연결로 `performance_schema.data_lock_waits`를 관찰한다. 알림 트랜잭션이 사용자 잠금을 기다리고 요청 행의 공유 잠금을 보유한 사실을 확인한다.
5. 대기를 해제해 같은 트랜잭션에서 실제 B→A 요청을 호출한다. 명령은 요청 행의 배타 잠금을 기다려 순환이 완성된다.
6. InnoDB 기록과 최종 DB 상태를 수집한 뒤 테스트 데이터를 제거한다.

이는 제품 코드의 잠금 단계와 동등한 상태를 테스트가 먼저 만들고, 이후 실제 서비스와 Outbox 코드를 실행한 결과다. 두 HTTP 요청의 시작부터 끝까지를 그대로 재생한 부하 테스트는 아니다.

## 마지막 실행의 증거

원문: [잠금 관찰·실제 SQL·InnoDB 기록](2026-10-16-friend-deadlock-evidence.txt).

- 친구 명령 연결 18: `users` 두 행의 X 잠금 보유, `friend_requests` 행의 X 잠금 대기.
- 알림 연결 19, 트랜잭션 2795: `friend_requests` 행의 S 잠금 보유, 알림 INSERT에서 수신자 `users` 행의 S 잠금 대기.
- Hibernate 오류: `ErrorCode: 1213, SQLState: 40001`.
- InnoDB: `WE ROLL BACK TRANSACTION (2)` — 친구 명령이 롤백되었다.
- 요청 결과: `CannotAcquireLockException`.
- 정리 전 최종 상태: pending 1건, friendship 0건, `FRIEND_ACCEPTED` 알림 0건. 최초 `FRIEND_REQUEST` Outbox만 `COMPLETED`.

S(공유) 잠금끼리는 함께 읽을 수 있지만 X(배타) 잠금과 충돌한다. 서로 상대가 가진 잠금을 기다리면서 진행할 수 없게 된 상태가 이번 데드락이다. 사용자 ID 정렬 잠금만으로는 다른 트랜잭션의 테이블 접근 순서까지 통일되지 않는다.

## 관련 코드와 수정 방향

- `FriendCommandTransactionalService.java:45-57`: 사용자 쌍 X 잠금 이후 friendship과 pending 요청 X 잠금을 획득한다.
- `NotificationOutboxRepository.java:90-125`: 요청/친구 대상 S 잠금을 먼저 잡고, 알림 INSERT에서 수신자 사용자 FK 검사에 따른 S 잠금을 뒤늦게 획득한다.
- `NotificationOutboxProcessor.java:65-71`: 이벤트의 알림들을 한 트랜잭션 안에서 순서대로 INSERT한다.
- `FriendCommandService.java:17-21`: 명령 커밋 뒤 즉시 알림 처리가 시작돼 다음 명령과 겹칠 수 있다.

**현재 순서**

```java
// 친구 명령: 사용자 → 친구 관계/요청
lockActivePair(pair);                        // users X
friendRequestRepository.findByUserPairForUpdate(lowId, highId); // request X

// 알림 저장: 대상 → 사용자
Long targetId = lockExistingTarget(draft.type(), draft.targetId()); // request/friendship S
jdbcTemplate.update("INSERT INTO notifications ...", ...);        // users FK S
```

**제안 순서 — 아직 적용하지 않은 설계 예시**

```java
// 한 이벤트의 모든 친구 알림 초안을 먼저 만든다.
List<NotificationDraft> drafts = handler.handle(event);
// 수신자 ID를 중복 제거·오름차순 정렬하고 모든 사용자 S 잠금을 대상 잠금보다 먼저 획득한다.
lockFriendNotificationRecipients(drafts);
for (NotificationDraft draft : drafts) {
    outbox.insertNotification(event.eventId(), draft); // 대상 S → INSERT
}
```

친구 알림에서도 사용자 → 대상 순서로 맞추는 방안을 우선 검토한다. 수락 이벤트에는 수신자가 둘이므로 알림 한 건마다 사용자/대상 잠금을 번갈아 잡지 않고 모든 수신자를 먼저 ID 순서대로 잠그는 것이 핵심이다. 추가 잠금이 사용자 삭제·친구 삭제와 충돌하지 않는지 확인하고, 공통 Outbox 경로로 확대할 경우 월간 리포트 등 다른 도메인의 잠금 순서도 검토해야 한다. 대상 공유 잠금만 제거하면 동시 대상 삭제와 FK 처리에 대한 기존 보장이 달라질 수 있으므로 우선안으로 삼지 않는다.

그 뒤 남는 데드락에는 전체 친구 명령 트랜잭션을 새 트랜잭션에서 제한 재시도하는 방안을 검토한다. 기존 트랜잭션 내부에서 SQL만 다시 실행하는 방식은 사용하지 않는다. 성공한 명령 결과에 대해서만 dispatcher가 호출되어야 한다. 이번 테스트는 잠금 순환 자체를 관찰하므로 재시도만 추가해서는 합격하지 않는다.

MySQL은 FK 검사가 부모 행에 공유 레코드 잠금을 설정한다고 설명하며, 데드락 완화를 위해 접근 순서를 통일하고 롤백된 트랜잭션을 다시 실행하도록 권고한다.

- [MySQL 8.4 — FK 검사와 잠금](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)
- [MySQL 8.4 — 데드락 처리와 잠금 순서](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html)

## 다음 검증 조건

제품 수정 후 새 회귀 검사에서 요청 S 잠금을 가진 사용자 대기가 사라지고, pending 0건·friendship 1건·수락 알림 2건·두 Outbox `COMPLETED`를 확인해야 한다. 이후 관련 테스트와 전체 빌드·커버리지 검사를 수행하고 CI의 기존 제외 설정을 복구한다. CI에 새 검사를 추가할 때는 전용 스키마 생성 및 앱 계정 권한 부여, 전용 `DB_URL`과 관리자 관찰 환경변수를 별도 단계에 제공해야 한다. 현재 CI의 `mulo` URL을 그대로 사용하면 안전 검사에서 거절된다.

당시 실제 사고 로그는 제공되지 않았으므로 이 경로가 당시 사고와 동일하다고 확정하지 않는다. 이번 기록은 현재 제품 코드에 존재하는 재현 가능한 데드락 경로의 증거다.
