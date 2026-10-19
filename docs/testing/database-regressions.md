# 관리자 DB 회귀 검사 실행

## 일반 작업

```bash
docker compose -f compose.test.yml up -d
./gradlew test
```

일반 `test`에는 아래 관리자 검사 5개가 포함되지 않는다. 두 클래스를 `src/test`에서 `src/integrationTest`로 이동했으므로 설정 미비로 생기는 skip도 일반 결과에 나타나지 않는다. 일반 테스트의 기존 격리 DB(127.0.0.1:3313/mulo_test) 준비 방식은 유지한다.

## 명시적인 DB 검사

```bash
./gradlew friendDeadlockTest notificationMigrationTest --rerun-tasks
```

| 명령 | 개수 | 책임 |
| --- | --- | --- |
| `friendDeadlockTest` | 2 | 실제 잠금 대기로 명령 선행·알림 선행 경로 검증 |
| `notificationMigrationTest` | 3 | 알림 migration의 이력 보존·제약·중단 조건 검증 |

두 태스크는 기존 integrationTest 출력 중 해당 클래스만 실행한다. `--rerun-tasks`로 실제 실행할 때 환경변수가 없으면 명확한 설정 오류로 실패하며, 테스트 내부의 조건부 skip은 제거했다. 같은 클래스는 전체 `integrationTest` 실행에도 포함되므로 그 명령을 사용할 때도 아래 환경이 필요하다.

## 필요한 환경

운영·공유 DB 대신 로컬 일회용 MySQL을 사용한다. 계정 값은 실행 환경에서 주입하며 저장소에 넣지 않는다.

### 친구 잠금 검사

- `DB_URL`: `jdbc:mysql://127.0.0.1:<테스트 포트>/mulo_friend_deadlock_it?serverTimezone=Asia/Seoul`
- `DB_USERNAME`, `DB_PASSWORD`: 애플리케이션 테스트 연결 계정
- `MULO_DEADLOCK_IT_ADMIN_URL`: 위 `DB_URL`과 정확히 동일
- `MULO_DEADLOCK_IT_ADMIN_USERNAME`, `MULO_DEADLOCK_IT_ADMIN_PASSWORD`: performance_schema 잠금과 InnoDB 상태를 관찰할 계정
- SpringBootTest 구동에 필요한 JWT·외부 서비스 테스트 설정은 기존 CI의 테스트 값 사용

친구 검사 스키마는 먼저 준비한다. Flyway가 테이블을 구성하며, 관찰 연결과 애플리케이션 연결은 같은 스키마를 사용한다.

### 알림 migration 검사

- `MULO_NOTIFICATION_IT_DB_URL`: `jdbc:mysql://127.0.0.1:<테스트 포트>/`
- `MULO_NOTIFICATION_IT_DB_USERNAME`, `MULO_NOTIFICATION_IT_DB_PASSWORD`: 테스트 스키마 CREATE/DROP 권한 계정

이 URL은 **DB 이름·쿼리 문자열 없이 `/`로 끝나는 로컬 서버 주소**다. 각 테스트가 무작위 `mulo_notification_it_*` 스키마를 생성한 뒤 제거한다. 친구 검사 URL과 용도가 다르다.

## CI와 결과

dev/prod의 PR·push 네 경로는 일회용 MySQL을 준비하고 `friendDeadlockTest notificationMigrationTest`를 실행한 후 일반 테스트를 수행한다. 관리자 설정 누락은 필수 검사 실패가 된다.

- `build/reports/tests/test/`: 일반 검사
- `build/reports/tests/friendDeadlockTest/`: 잠금 검사 2개
- `build/reports/tests/notificationMigrationTest/`: migration 검사 3개
- XML 결과는 각각 `build/test-results/<태스크>/`에 저장한다.
- CI 실패 보고서 artifact는 전체 `build/reports/tests/`를 포함한다.

기존 `@EnabledIfEnvironmentVariable`·`assumeTrue`가 환경 부족을 skip으로 표시하던 구조에서 실행 태스크 자체를 분리한 것이다. 테스트 검증 내용은 보존한다.

근거: [Gradle 별도 테스트 구성](https://docs.gradle.org/current/userguide/java_testing.html), [JUnit Assumptions의 중단 의미](https://docs.junit.org/current/api/org.junit.jupiter.api/org/junit/jupiter/api/Assumptions.html).
