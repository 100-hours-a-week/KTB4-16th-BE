# 친구 알림 잠금 순서 개선 결과 — 작업 #248

> **2026-10-18 정책 대체:** 아래 정책과 검증 수치는 2026-10-16 당시의 원문 기록이다. [위키 #248](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/248) 사용자 확정에 따라 요청이 사라진 `FRIEND_REQUEST`는 신규 INSERT를 생략하고 retry 증가 없이 Outbox를 `COMPLETED`로 완료한다. 이미 저장된 이력의 최신 표시 기준은 서비스 정책 `NOTI-007`·`NOTI-008`·`NOTI-014`이며, 아래 결과는 새 정책의 검증 결과를 의미하지 않는다.

## 범위와 근거

- 브랜치: `fix/friend-notification-deadlock-248` (원본 백엔드 폴더, `origin/develop` 기준).
- 기존 재현 원문: [수정 전 InnoDB 증거](2026-10-16-friend-deadlock-evidence.txt), SHA-256 `80873c94080750e600140062ed7857a87ed077ab4aa979b601b0e40f9691394f`.
- 기존 재현 테스트: `FriendDeadlockMysqlIntegrationTest`에서 알림이 사용자 행을 기다리는 동안 요청 행 S 잠금을 보유했고, 역방향 요청에 MySQL `1213 / 40001`이 발생했다. 이 테스트는 수정 전 이번 실행에서도 같은 이유로 실패했다.
- 실제 운영 사고 로그는 제공되지 않았다. 이 기록은 현재 코드에서 재현한 잠금 순환의 전후 비교이다.

## 수정 전후

| 경로 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 친구 명령 | 사용자 2명 X → 요청 또는 친구 대상 X | 동일 |
| 친구 요청 알림 | 요청 대상 S → 알림 INSERT의 사용자 FK S | 수신자 사용자 S → 요청 대상 S → 알림 INSERT |
| 친구 성립 알림 | 친구 대상 S → 첫 사용자 FK S → 둘째 사용자 FK S | 수신자 사용자 ID 오름차순 S 전체 → 친구 대상 S → 알림 INSERT 2건 |
| 다른 알림 | 기존 순서 | 동일 |

`NotificationOutboxProcessor.java:59-80`의 `processLocked`는 한 이벤트의 초안을 한 번만 만들고, 친구 유형에서만 수신자 잠금을 먼저 호출한다. `NotificationOutboxRepository.java:90-95`의 `lockExistingRecipientsInIdOrder`는 중복 ID를 제거하고 정렬한 뒤 각 사용자 행을 `FOR SHARE`로 조회한다. 기존 대상 잠금과 INSERT는 `NotificationOutboxRepository.java:97-132`에 있다. 대상 공유 잠금과 Outbox의 트랜잭션·Savepoint 경계는 그대로 둔다. 사용자가 삭제되어 행이 없으면 기존 알림 INSERT의 FK 실패 및 재시도 경로를 유지한다.

최초 요청 알림보다 역방향 요청이 먼저 친구를 성립시켜 요청 행이 사라져도, 사용자 결정에 따라 요청 알림은 `friend_request_id = NULL`로 저장하고 Outbox는 `COMPLETED`로 처리한다. `FriendDeadlockMysqlIntegrationTest.java:141-167`에서 이 상태를 검사한다.

## 검증

1. 일회용 MySQL 8.4.11의 `mulo_friend_deadlock_it`에서 수정 전 회귀 테스트가 실패했다. 잠금 관찰 `requestSharedLockHeld=true`, 역방향 요청 `CannotAcquireLockException`, MySQL `1213 / 40001`, pending 1건·friendship 0건.
2. 수정 후 같은 회귀 테스트가 통과했다. 포트 3308과 CI와 같은 포트 3307에서 각각 확인했다. 마지막 실행은 `requestSharedLockHeld=false`, pending 0건·friendship 1건·수락 알림 2건·최초 요청 알림 1건이었다. 요청과 성립 Outbox 모두 `COMPLETED`, 오류 코드 없음.
3. 기존 `FriendMysqlIntegrationTest` 5개와 `NotificationMysqlIntegrationTest` 5개가 통과했다.
4. 포트 3307의 전용 DB에서 `clean build jacocoTestCoverageVerification --rerun-tasks`가 통과했다: 전체 714개, 실패 0개, 건너뜀 4개. 별도 데드락 회귀 테스트 1개도 통과했다.
5. 첫 전체 실행은 포트 3308에서 26개가 실패했다. 실패한 기록·날씨 테스트는 `127.0.0.1:3307`을 요구한다. 일회용 DB를 3307로 다시 생성하고 같은 전체 명령을 실행해 실패 0개를 확인했다.
6. CI dev/prod의 PR·push 경로 네 곳에 전용 스키마/검사 단계를 추가하고, 친구 동시성 테스트 2개를 제외하던 임시 init script를 제거했다. YAML 두 파일은 파싱 확인했다. GitHub Actions 자체 실행 결과는 아직 없다.

## 재실행 조건

테스트는 **일회용 로컬 MySQL**에만 실행한다. 전용 컨테이너를 `127.0.0.1:3307`에 열고 `mulo_friend_deadlock_it`와 `mulo_weather_it` 스키마 및 테스트 계정을 준비한다. 프로젝트 `.env`의 앱 설정을 읽은 뒤 DB 접속값만 전용 스키마로 덮어쓴다. 계정 비밀번호는 Git에 보관하지 않는다.

```text
DB_URL=jdbc:mysql://127.0.0.1:3307/mulo_friend_deadlock_it?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
DB_USERNAME=<전용 테스트 계정>
DB_PASSWORD=<전용 테스트 비밀번호>
MULO_DEADLOCK_IT_ADMIN_URL=<DB_URL과 정확히 같은 URL>
MULO_DEADLOCK_IT_ADMIN_USERNAME=<performance_schema 관찰 권한 계정>
MULO_DEADLOCK_IT_ADMIN_PASSWORD=<관찰 계정 비밀번호>
JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Seoul
```

```bash
./gradlew test --tests com.ktb4.team16.mulo.friend.service.FriendDeadlockMysqlIntegrationTest --rerun-tasks
```

전체 검사에서는 `MULO_DEADLOCK_IT_ADMIN_*`을 설정하지 않고 `MULO_WEATHER_IT_DB_*`을 `127.0.0.1:3307/mulo_weather_it`로 지정한다. 위 전용 검사는 환경변수가 없으면 건너뛰므로 CI도 별도 단계에서 먼저 실행한다.

```bash
./gradlew clean build jacocoTestCoverageVerification --rerun-tasks
```

MySQL은 외래 키 검사 때 부모 행에 공유 잠금을 잡는다고 설명한다. 잠금 순서를 통일하는 방법도 공식 데드락 대응 방법이다.

- [MySQL 8.4: InnoDB가 설정하는 잠금](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)
- [MySQL 8.4: 데드락을 줄이는 방법](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html)
