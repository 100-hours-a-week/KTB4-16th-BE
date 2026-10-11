# 알림 스키마 V7 로컬 검증 기록

검증일: 2026-10-11 (Asia/Seoul)

작업 브랜치: `feat/notification-207`

대상: 로컬 MySQL 8.4의 `mulo` DB

## 적용 전 확인

| 항목 | 결과 |
|---|---|
| `AI_PLAYLIST` 알림 수 | 0건 |
| 전체 `notifications` 행 | 0건 (`GROUP BY type` 결과 없음) |
| 최신 Flyway 성공 버전 | V6 |
| 스키마 | 5종 ENUM, `recommendation_playlist_id`·플레이리스트 FK/UNIQUE, `uk_notifications_friendship` 존재 |

적용 전에 다음 읽기 전용 SQL을 실행했다.

```sql
SELECT COUNT(*) AS ai_playlist_count
FROM notifications WHERE type = 'AI_PLAYLIST';
SELECT type, COUNT(*) AS row_count FROM notifications GROUP BY type;
SHOW CREATE TABLE notifications;
SHOW INDEX FROM notifications;
SELECT installed_rank, version, success
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
```

## 적용과 적용 후 확인

기존 로컬 `.env`를 실행 환경에만 로드하고 `./gradlew test --tests 'com.ktb4.team16.mulo.MuloApplicationTests'`로 Spring/Flyway를 시작했다. 테스트는 성공했고 JPA `ddl-auto: validate` 설정으로 컨텍스트가 시작됐다. `mulo` DB에 수동 `ALTER TABLE`은 실행하지 않았다.

| 항목 | 결과 |
|---|---|
| 최신 Flyway 이력 | V7, `success = 1` |
| `notifications` 행 | 적용 전후 0건 |
| `type` | `MONTHLY_REPORT_CREATED`, `MATCH_COMPLETED`, `FRIEND_REQUEST`, `FRIEND_ACCEPTED` |
| 제거 | `AI_PLAYLIST` ENUM 값, `recommendation_playlist_id`, `fk_notifications_playlist`, `uk_notifications_playlist`, `uk_notifications_friendship` |
| 추가 | `source_event_id CHAR(36) NULL`, `uk_notifications_source_event_user (source_event_id, user_id)`, 비고유 `idx_notifications_friendship` |
| 유지 | `fk_notifications_friendship ON DELETE SET NULL`; `chat_room_id` 컬럼과 비 FK 인덱스 |

적용 후 `SHOW CREATE TABLE notifications`, `SHOW INDEX FROM notifications`, `flyway_schema_history`를 다시 읽어 확인했다. 격리 MySQL 스키마에 V1~V6을 먼저 적용하는 `NotificationMigrationMysqlIntegrationTest` 2개도 통과했다. 해당 테스트는 AI 이력 1건일 때 V7이 삭제 전에 거부하는 경로와, 비 AI 이력·두 수신자·중복 방지·친구 관계 FK를 검증한다.

전체 테스트는 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10 ./scripts/test/run-weather-mysql-integration.sh`로 일회용 MySQL 8.4 컨테이너에서 실행했다. 결과는 562개 중 실패 0개, 건너뜀 2개였다. 건너뛴 2개는 이 스크립트가 `MULO_NOTIFICATION_IT_DB_*`를 제공하지 않아 새 격리 스키마 테스트가 의도대로 건너뛴 것이다. 두 테스트는 위의 별도 실행에서 모두 통과했다. `./gradlew checkstyleTest`도 통과했다. 기존 날씨/동시성 통합 테스트의 Flyway 성공 버전 기대값은 V6 기준 6에서 V7 기준 7로 조정했다. 동시성 테스트가 요구하는 풀 크기 10은 전체 테스트 명령에만 지정했으며 서비스 기본값 3은 변경하지 않았다.

## 다른 DB 적용 조건

CI/develop 및 배포 DB의 연결 정보는 `.github/workflows/CI.yaml`, `.github/workflows/CD.yml`의 GitHub Secrets로 제공된다. 이 작업에서는 해당 DB에 접근하거나 0건을 검증하지 못했다. **로컬 0건을 다른 DB의 0건으로 간주하지 않는다.** 각 환경에 V7을 배포하기 전에 운영 권한을 가진 담당자가 위 SQL을 해당 DB에서 직접 실행하고 다음을 확인해야 한다.

1. `AI_PLAYLIST` 행이 정확히 0건이다. 조회할 수 없거나 1건 이상이면 적용을 중단하고 데이터 처리 결정을 다시 받는다.
2. 최신 Flyway 성공 버전은 V6이고 실패 이력이 없으며, `SHOW CREATE TABLE`과 인덱스가 V6 기준 구조와 일치한다. 다른 버전이나 스키마 변경이 있으면 V7 파일명·순서를 재검토한다.
3. 마이그레이션은 Flyway로 적용한다. 적용 직후 V7 성공 이력, ENUM·컬럼·FK·인덱스, 기존 알림 행 수를 다시 확인한다.

V7의 첫 DDL은 `AI_PLAYLIST`를 거부하는 CHECK를 추가하므로 이력이 남은 DB에서는 제거 DDL 전에 실패한다. MySQL `ALTER TABLE`은 [암묵적으로 커밋](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)된다. 중간 단계에서 실패하면 `SHOW CREATE TABLE`, `SHOW INDEX`, `flyway_schema_history`를 비교한 뒤 복구 마이그레이션을 검토해야 하며, V7을 무작정 다시 실행하거나 이미 적용된 파일을 수정하지 않는다.
