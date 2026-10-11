# 1:1 매칭 알림 적용 전 이력 확인 기록

## 확인 목적과 기준

신규 매칭은 같은 `vote_question_id`에서 A 1명+B 1명으로 구성한다. 모든 적용 환경에서 기존 매칭·채팅방·`MATCH_COMPLETED` Outbox 이력이 0건임을 확인하기 전에는 1:1 전용 알림 처리를 배포하지 않는다. 기존 이력이 있거나 DB 조회가 불가능한 환경은 `배포 보류`다. 확인되지 않은 결과를 0건으로 간주하지 않는다.

Backend V6은 `matches`를 매칭 이력 테이블로 만들고 `match_requests`에 `MATCHING` 상태와 `match_id`를 둔다. `chat_room_id`는 별도 Chat DB가 소유하며, Backend에서 Chat 테이블과 cross-DB FK를 제거했다 (`src/main/resources/db/migration/V6__separate_matching_and_chat_schema.sql:6-17,26-59`). V7은 알림 테이블만 변경하며 Outbox 테이블을 만들지 않는다 (`src/main/resources/db/migration/V7__prepare_notification_outbox_schema.sql:1-29`).

## 환경별 결과

| 환경 | 확인 시각 (KST) | 확인 범위 | 결과 | 적용 상태 |
|---|---|---|---|---|
| 로컬 Backend MySQL `mulo` | 2026-10-11 17:26:27 (`NOW()`, 세션 `SYSTEM`) | `matches`, `match_requests`, `MATCH_COMPLETED` 알림, 현재 DB의 Outbox 유사 테이블 | `matches` 0건, `match_requests` 0건, `MATCH_COMPLETED` 알림 0건, Outbox 테이블 0개 | Backend 이력 확인 완료 |
| 로컬 Chat DB | 같은 로컬 MySQL 인스턴스에서 위 시각 확인 | 별도 사용자 스키마 탐색 | 사용자 스키마는 `mulo`만 보였다. Backend 작업 공간에 Chat DB 스키마·접속 설정이 없어 별도 Chat DB의 방·참가자 이력은 조회하지 못했다. | 미확인 · 배포 보류 |
| develop DB | 미확인 | `.github/workflows/CI.yaml:353-355`의 GitHub Secrets `DB_URL_Develop` 등으로 접속 | 이 실행 환경에서 GitHub Secrets와 develop DB 접속 권한을 사용할 수 없어 조회하지 못했다. | 미확인 · 배포 보류 |
| production DB | 미확인 | `.github/workflows/CD.yml:64-68`의 GitHub Secrets `DB_URL` 등으로 접속 | 이 실행 환경에서 GitHub Secrets와 production DB 접속 권한을 사용할 수 없어 조회하지 못했다. | 미확인 · 배포 보류 |

따라서 로컬 Backend DB의 빈 이력은 확인했지만 **모든 환경의 0건 조건은 아직 충족되지 않았다.** 별도 Chat DB와 develop/production DB 확인 전까지 1:1 전용 Outbox 처리기 배포는 보류한다.

## 로컬 Backend에서 실행한 읽기 전용 SQL

다음 조회는 `mulo-mysql` 컨테이너의 MySQL 8.4에서 실행했다. 자격 증명은 컨테이너 환경변수로만 전달했으며 문서나 명령 출력에 기록하지 않았다.

```sql
SELECT NOW() AS checked_at, @@session.time_zone AS session_time_zone;

SELECT 'matches' AS check_name, COUNT(*) AS row_count FROM matches;
SELECT 'match_requests' AS check_name, COUNT(*) AS row_count FROM match_requests;
SELECT status AS check_name, COUNT(*) AS row_count FROM match_requests GROUP BY status;
SELECT 'match_completed_notifications' AS check_name, COUNT(*) AS row_count
FROM notifications WHERE type = 'MATCH_COMPLETED';
SELECT TABLE_NAME AS outbox_table
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE '%outbox%';
SELECT 'outbox_tables' AS check_name, COUNT(*) AS row_count
FROM information_schema.tables
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE '%outbox%';

SELECT 'matches_with_chat_room_id' AS check_name, COUNT(*) AS row_count
FROM matches WHERE chat_room_id IS NOT NULL;
SELECT SCHEMA_NAME AS local_schema
FROM information_schema.schemata
WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys')
ORDER BY SCHEMA_NAME;
```

관측 결과는 `matches=0`, `match_requests` 상태별 행 없음, `MATCH_COMPLETED` 알림 0건, Outbox 테이블 없음, `matches.chat_room_id IS NOT NULL` 0건, 사용자 스키마 `mulo`만 확인이다. 이 조회는 로컬 Backend 연결에 한정된다.

## 다른 환경의 적용 전 확인 절차

1. 대상 환경과 소유 DB를 식별하고, 각 Backend DB에서 위의 `matches`, `match_requests`, 알림 조회를 읽기 전용으로 실행한다. Outbox 테이블은 `information_schema.tables`에서 존재 여부를 확인한 뒤 실제 이름·payload 컬럼 구조를 조회한다.
2. 별도 Chat DB의 실제 스키마를 해당 소유 저장소/DB에서 확인한 후, 채팅방 및 참여자 이력을 읽기 전용으로 확인한다. 스키마가 확인되기 전에는 테이블명이나 SQL을 가정하지 않는다.
3. Backend `matches` 또는 Chat DB에 과거 이력이 있거나, `MATCH_COMPLETED` Outbox/알림 행이 있으면 1:1 전용 처리를 배포하지 않는다. 행을 삭제·변환하지 말고 과거 4인 계약의 보존·호환 정책을 다시 결정한다.
4. DB 접속·조회 권한이 없거나 결과를 기록하지 못하면 해당 환경은 `미확인 · 배포 보류`로 둔다.
5. 환경별 조회 시각, DB 식별자(비밀정보 제외), 읽기 전용 SQL, 행 수, 확인자와 적용 판정을 기록한 뒤에만 전체 환경 0건 조건을 재평가한다.

두 참가자의 Chat DB 등록 완료를 Backend가 어떻게 신뢰성 있게 확인할지는 별도 프로토콜 설계가 필요하다. V6의 `matches`와 논리 `chat_room_id`만으로는 Chat DB 참가자 등록이 완료됐다고 판단할 수 없다. 이 확인 계약이 마련되기 전에는 `MATCH_COMPLETED` Outbox 발행 구현도 배포 게이트를 통과하지 못한다.
