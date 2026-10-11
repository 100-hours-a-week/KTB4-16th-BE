# 알림 테이블 스키마 마이그레이션 설계

## 목적과 범위

MULO V2 알림 Outbox 도입 전에 기존 `notifications` 테이블을 데이터 손실 없이 정비한다. 알림 첫 구현이므로 `AI_PLAYLIST` 유형과 플레이리스트 연결 컬럼을 제거하되, 적용 대상 DB마다 해당 이력이 0건임을 먼저 확인한다. 역방향 친구 요청으로 하나의 친구 관계에 두 사용자의 알림을 연결할 수 있게 하며, 신규 알림의 이벤트별 사용자 중복을 DB에서 방지한다.

이 문서의 범위는 `notifications` 스키마와 해당 마이그레이션 검증이다. Outbox 테이블·처리기, 알림 API, 매칭과 별도 Chat DB 사이의 완료 프로토콜은 후속 설계 단위다. 기존 V1~V6 마이그레이션은 변경하지 않는다.

## 확인된 기준 상태

- `src/main/resources/db/migration/V1__init_schema.sql:347-389`는 `AI_PLAYLIST`를 포함한 5종 ENUM, `recommendation_playlist_id` FK, `friendship_id` FK와 단일 컬럼 UNIQUE를 정의한다.
- `src/main/resources/db/migration/V6__separate_matching_and_chat_schema.sql:51-59`는 `notifications.chat_room_id`의 FK를 제거하고 Chat DB 소유 ID를 논리적 참조로 남긴다.
- 2026-10-11 로컬 MySQL 8.4.11 확인 결과, Flyway V1~V6은 성공 적용됐고 `notifications`는 0건이다. `friendships` 1건, `monthly_reports` 1건, `recommendation_playlists` 2건이 있다. 이 수치는 다른 환경의 데이터 상태를 뜻하지 않는다. 다른 DB의 0건 여부는 아직 확인되지 않았다.
- 이슈 기준은 [wiki #207](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/207)이다. 현재 구현 기준 API 파일은 `specs/openapi/MULO_OpenAPI_v2.8.*`이며, 첨부 통합 프롬프트의 최신 정책 결정을 우선한다.

## 원본과 목표

| 대상 | 원본 | 목표 | 근거 |
|---|---|---|---|
| `type` | 5종 ENUM에 `AI_PLAYLIST` 포함 | `AI_PLAYLIST`를 제거한 4종 ENUM | 통합 프롬프트 §4, 사용자 첫 구현·0건 확인 후 제거 결정 |
| `recommendation_playlist_id` | nullable FK, `ON DELETE SET NULL`, UNIQUE | FK·UNIQUE·컬럼 제거 | 신규 `AI_PLAYLIST` 알림을 생성하지 않음 |
| `friendship_id` | nullable FK, `ON DELETE SET NULL`, UNIQUE | FK와 삭제 동작은 유지하고 UNIQUE를 일반 인덱스로 교체 | 통합 프롬프트 §5.4의 역방향 자동 수락: 같은 friendship에 수신자 2명 |
| `source_event_id` | 없음 | nullable `CHAR(36)` 추가, Outbox FK 없음 | 통합 프롬프트 §15 |
| 사용자별 이벤트 중복 | 제약 없음 | `UNIQUE (source_event_id, user_id)` 추가 | 통합 프롬프트 §7.3·§15 |
| `chat_room_id` | V6 이후 nullable 컬럼, FK 없음 | 이번 마이그레이션에서는 변경 없음 | Chat DB 분리 상태 유지 |

신규 V2 알림의 `source_event_id` 기록 의무는 후속 알림 생성 코드에서 검증한다. 컬럼을 nullable로 두는 이유는 기존 DB가 이미 `notifications`를 가질 수 있고, 과거 행에는 대응하는 Outbox 이벤트가 없기 때문이다. MySQL UNIQUE 인덱스는 nullable 컬럼의 여러 `NULL`을 허용한다. [MySQL 8.4 CREATE INDEX](https://dev.mysql.com/doc/refman/8.4/en/create-index.html).

## 마이그레이션 구성과 순서

최신 `develop`의 마이그레이션 번호를 다시 확인하고 새 버전 파일을 추가한다. 현재 확인된 마지막 번호가 V6이므로, 이 작업의 파일명은 V7로 계획한다. 구현 전에 더 높은 버전이 추가됐다면 파일명과 적용 순서를 재검토한다.

1. 적용 대상 DB마다 `SELECT COUNT(*) FROM notifications WHERE type = 'AI_PLAYLIST'`, `SELECT type, COUNT(*) FROM notifications GROUP BY type`, `SHOW CREATE TABLE notifications`를 읽고, `AI_PLAYLIST`가 0건인지 확인한다. 다른 DB를 조회할 권한이 없거나 1건 이상이면 그 환경의 적용을 중단한다. 행을 자동 삭제하거나 유형을 바꾸지 않는다. 새 Flyway 파일의 첫 DDL은 `CHECK (type <> 'AI_PLAYLIST')`를 추가해 기존 행이 있으면 실패하도록 한다. 이후 ENUM을 줄이는 DDL에서 해당 CHECK를 함께 제거한다. [MySQL 8.4 ALTER TABLE](https://dev.mysql.com/doc/refman/8.4/en/alter-table.html).
2. `source_event_id CHAR(36) NULL`, `UNIQUE(source_event_id, user_id)`, 일반 `INDEX(friendship_id)`를 추가한다.
3. 일반 인덱스가 실제 생성됐음을 검증한 뒤 `uk_notifications_friendship`만 제거한다.
4. `fk_notifications_playlist`, `uk_notifications_playlist`, `recommendation_playlist_id`를 제거하고, `type`을 4종 ENUM으로 변경한다.
5. `fk_notifications_friendship`과 `ON DELETE SET NULL`이 남고 `AI_PLAYLIST` 유형·연결 컬럼은 사라졌는지 `SHOW CREATE TABLE`로 확인한다.

인덱스 추가를 UNIQUE 제거보다 앞에 둔다. MySQL은 FK 검사에 쓰이는 인덱스를 요구하고, 필요한 인덱스를 바로 삭제하는 작업을 제한한다. [MySQL 8.4 FOREIGN KEY Constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html). 변경 파일에는 각 DDL의 책임을 짧은 주석으로 남긴다.

MySQL의 `ALTER TABLE`은 암묵적으로 커밋된다. 따라서 한 파일 안의 여러 DDL을 하나의 롤백 가능한 트랜잭션으로 취급하지 않는다. 로컬 검증에서 각 단계의 결과와 실패 후 재실행 조건을 확인하고, 적용 환경에서는 Flyway 이력과 실제 스키마를 함께 확인한다. [MySQL 8.4 Implicit Commit](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html).

## 검증 기준

1. 별도 로컬 검증 스키마에서 V1~V6을 재현한다. `AI_PLAYLIST` 행이 0건일 때 새 마이그레이션이 적용되고, 1건 이상이면 사전 검증에서 중단되는지 확인한다. 후자의 행은 삭제하거나 변환하지 않는다.
2. 동일 `friendship_id`를 참조하는 서로 다른 두 수신자의 알림 INSERT가 성공해야 한다. FK가 없는 friendship ID는 실패해야 한다.
3. 동일한 non-null `(source_event_id, user_id)`의 두 번째 INSERT는 실패하고, 같은 이벤트의 다른 수신자는 성공해야 한다. 기존 `source_event_id = NULL` 행은 유지돼야 한다.
4. `SHOW CREATE TABLE`과 `SHOW INDEX`로 유지 대상 FK·인덱스와 4종 ENUM을 확인한다. 기존 로컬 `mulo` DB에는 새 Flyway 파일을 통해 적용하고 이력의 성공 여부를 확인한다.
5. Spring 컨텍스트의 Flyway 적용과 `ddl-auto=validate` 통과를 확인한다. 광범위 테스트에서 별도 3307 포트 통합 DB를 요구하는 테스트는 해당 환경을 마련한 뒤 별도 검증한다.

로컬 DB의 알림 0건은 다른 적용 환경의 0건을 증명하지 않는다. CI·운영 DB 연결 정보는 GitHub Secrets에 있으므로 현재 작업 공간에서 직접 확인하지 못했다. 실제 로컬 `mulo` DB의 기존 사용자·친구·리포트·플레이리스트 행은 삭제하거나 초기화하지 않는다.

## 문서와 후속 작업의 경계

마이그레이션 구현 시 `specs/MULO_서비스_정책_정의서_v2.1.md`에는 알림 유형 4종을 반영한다. `specs/MULO_테이블_정의서_v8.1.md`에는 `source_event_id`·복합 UNIQUE와 `friendship_id` 일반 인덱스, `AI_PLAYLIST` 연결 제거를 반영하고, V6에서 제거된 `chat_room_id` FK 설명을 실제 스키마에 맞춘다. API 응답 형태를 변경하지 않으므로 OpenAPI v2.8의 알림 경로는 알림 API 구현 단계에서 동기화한다.

Outbox 테이블과 알림 Handler가 아직 없으므로 이 마이그레이션만으로 신규 알림을 생성하지 않는다. 채팅방은 별도 Chat DB가 소유하므로, 통합 프롬프트의 단일 원본 트랜잭션 표현을 현재 V6 구조에 그대로 적용하지 않는다.
