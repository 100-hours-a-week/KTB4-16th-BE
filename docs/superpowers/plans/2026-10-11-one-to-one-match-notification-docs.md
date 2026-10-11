# 1:1 매칭 알림 및 관련 문서 반영 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 신규 매칭 A 1명+B 1명과 `MATCH_COMPLETED` 수신자 2명 계약을 현행 정책·테이블·OpenAPI 문서에 일치시키고, 모든 환경의 과거 이력 확인 조건을 기록한다.

**Architecture:** 이 작업은 문서 변경만 수행한다. Backend의 `matches`와 별도 Chat DB의 방·참가자 완료를 알림 발행 조건으로 기술하되, 두 DB 사이의 완료 프로토콜을 구현된 것처럼 설명하지 않는다. 기존 V7 알림 스키마, API 구조, 과거 v2.7 문서는 유지한다.

**Tech Stack:** Markdown, OpenAPI 3.0.3 YAML/JSON, Swagger UI HTML, Ruby 표준 `json`/`yaml` 파서, Git.

**Spec:** `docs/superpowers/specs/2026-10-11-one-to-one-match-notification-design.md`

## Global Constraints

- 브랜치 `feat/notification-207`에서 작업하고 `RULE.md`의 `docs: ... #207` 커밋 형식을 따른다.
- 범위는 알림 계약과 관련 문서까지다. 매칭·채팅 Java 구현, Outbox 구현, 배포, Flyway V1~V7 수정은 포함하지 않는다.
- 같은 `vote_question_id`에서 A 1명+B 1명만 매칭한다. `MATCH_COMPLETED`는 매칭당 Outbox 1건, 서로 다른 수신자 알림 2건, `matchParticipantCount: 2`, `recipientUserIds` 2개다.
- 이벤트 발행은 Chat DB에서 두 참가자 등록 완료를 신뢰할 수 있게 확인한 뒤에만 가능하다. 그 확인 프로토콜은 후속 설계 과제다.
- 모든 적용 환경의 기존 4인 매칭·채팅방·Outbox 이력 0건 확인 전에는 1:1 전용 처리기를 배포하지 않는다. 접근 불가 환경은 미확인으로 기록한다.
- OpenAPI v2.8의 경로·필드·상태코드와 V7 스키마를 바꾸지 않는다. v2.7 산출물도 보존한다.
- `AGENTS.md` 사용자 지시에 따라 변경 기록에는 원본 문구·수정 문구·파일/행 근거를 함께 제시한다. 새 메서드를 만드는 작업은 없다.

## Review Focus

- 같은 선택지 사용자 2명만 대기하는 경우: A1+B1 충족으로 설명되지 않아야 한다. Task 1의 구성 문구 검사.
- 같은 수신자 ID가 두 번 들어온 경우: 수신자 2명으로 설명되지 않아야 한다. Task 1의 Payload 계약 검사.
- 문서 일부에 4인/4명/A2+B2가 남은 경우: 현행 v2.8·정책·테이블 검색에서 모두 발견돼야 한다. Tasks 1–2의 잔여 검색.
- OpenAPI YAML·JSON·HTML이 서로 다른 경우: 세 구조 비교가 실패해야 한다. Task 2의 파서 검사.
- 적용 환경에 과거 행이 있거나 조회 권한이 없는 경우: 배포 가능으로 기록되지 않아야 한다. Task 3의 환경별 결과 검사.

---

### Task 1: 정책서와 테이블 정의서의 2인 계약

**Files:**
- Modify: `specs/MULO_서비스_정책_정의서_v2.1.md:281,298-300,368`
- Modify: `specs/MULO_테이블_정의서_v8.1.md:757,766,792,800,823,841,848-849,862,869,903,905,911`

**Interfaces:**
- Consumes: 승인된 설계의 A1+B1, 채팅 참여자 2명, Outbox 1건·알림 2건 계약.
- Produces: 정책 ID와 테이블 설명이 같은 2인 구성을 가리키며, 후속 OpenAPI 작업의 문구 기준이 된다.

- [ ] **Step 1: 원문을 확인한다.** 위 행에서 A2+B2·4인·4명·4개 요청 표현을 기록하고, `MATCH-012`의 FIFO, 23:00 마감, 재입장 금지 등 인원과 무관한 규칙은 그대로 둔다.
- [ ] **Step 2: 정책 문구를 고친다.** `MATCH-001`: “A 선택 사용자 2명 + B 선택 사용자 2명 … 4인” → “A 선택 사용자 1명 + B 선택 사용자 1명 … 1:1”; `CHAT-001`·`CHAT-003`: “4명” → “2명”; `CHAT-002`·`SYNC-002`: “A2+B2” → “A1+B1”. `## 알림`의 `NOTI-010` 뒤에 `NOTI-011` “매칭 완료 알림” 행을 추가해, 두 Chat DB 참가자의 등록 완료를 확인한 매칭당 `MATCH_COMPLETED` Outbox 1건·서로 다른 두 사용자 알림 2건, Payload v1의 `matchParticipantCount=2`와 중복 없는 `recipientUserIds` 정확히 2개를 명시한다. A/B 판별은 기존 `vote_answers.selected_option` 설명을 따른다.
- [ ] **Step 3: 테이블 설명을 고친다.** 위 지정 행의 “A 선택 2명+B 선택 2명”과 “4인/4명/4개 요청”을 각각 A1+B1과 2인/2명/2개 요청으로 바꾼다. `rule_chat_room_join`은 실제 입장 전 참가자 2명 선등록, `rule_chat_room_delete`는 두 참가자 모두 퇴장 또는 자정이라는 기존 수명 규칙을 유지한다. V6의 `MATCHING`, `matches.match_id`, Chat DB 분리와 충돌하는 기존 V1식 컬럼·FK·상태 설명은 이 숫자 변경으로 추정 수정하지 않는다.
- [ ] **Step 4: 문서 검증을 한다.** `rg -n 'A2\+B2|A 선택 (사용자 )?2명|B 선택 (사용자 )?2명|4인|4명|4개 요청' specs/MULO_서비스_정책_정의서_v2.1.md specs/MULO_테이블_정의서_v8.1.md`의 결과가 비어 있어야 한다. `rg -n 'MATCH-001|MATCH_COMPLETED|matchParticipantCount|recipientUserIds|CHAT-00[1-3]|rule_match_complete|rule_chat_room_match_composition'`로 새 계약을 확인하고 `git diff --check`를 통과시킨다. 테스트 코드는 문서만 수정하므로 추가하지 않는다.
- [ ] **Step 5: 문서 변경을 커밋한다.** 두 파일만 `git add`하고 `git commit -m 'docs: 매칭 및 채팅 1대1 정책 반영 #207'`.

### Task 2: OpenAPI v2.8 세 산출물 동기화

**Files:**
- Modify: `specs/openapi/MULO_OpenAPI_v2.8.yaml:4518,4670,4915-4923,5168`
- Modify: `specs/openapi/MULO_OpenAPI_v2.8.json:6369,6573,6927,7302`
- Modify: `specs/openapi/MULO_Swagger_UI_v2.8.html:6384,6588,6942,7317`

**Interfaces:**
- Consumes: Task 1의 A1+B1·2인 정책 문구.
- Produces: 의미가 같은 YAML·JSON·HTML OpenAPI v2.8. 경로·필드·상태코드는 변경하지 않는다.

- [ ] **Step 1: 원본 세트를 비교한다.** 매칭 생성의 “A 선택 사용자 2명 + B 선택 사용자 2명 … 4인”, 상태 조회의 “4인 매칭”, 채팅방 정보 예시의 4개 `participants`, 퇴장의 “4명 모두”를 YAML·JSON·HTML에서 각각 찾는다.
- [ ] **Step 2: YAML의 설명과 예시를 수정한다.** 매칭 생성은 A 1명+B 1명, 2인 방으로, 상태 조회는 1:1 매칭으로, 퇴장은 두 참가자 모두 퇴장 시 삭제로 고친다. `chatRoomId: 30` 예시의 `participants`는 기존 첫 두 항목만 남긴다. `WAITING/MATCHED/EXPIRED`, 응답 스키마, `chatRoomId` 필드와 경로는 그대로 둔다.
- [ ] **Step 3: JSON과 HTML 내장 `spec`에 동일 변경을 반영한다.** 기존 직렬화 구조와 들여쓰기를 보존한다. v2.7 YAML·JSON·HTML은 수정하지 않는다.
- [ ] **Step 4: 세 구조와 잔여 문구를 검사한다.** 아래 명령 결과가 `[true, true]`여야 한다. `rg -n 'A 선택 사용자 2명|B 선택 사용자 2명|4인 매칭|4인 채팅방|4명 모두' specs/openapi/MULO_OpenAPI_v2.8.yaml specs/openapi/MULO_OpenAPI_v2.8.json specs/openapi/MULO_Swagger_UI_v2.8.html`은 결과가 비어 있어야 한다. 세 문서의 채팅방 예시 `participants`가 정확히 2개인지 확인하고 `git diff --check`를 통과시킨다.

  ```bash
  ruby -rjson -ryaml -e 'y=YAML.load_file("specs/openapi/MULO_OpenAPI_v2.8.yaml"); j=JSON.parse(File.read("specs/openapi/MULO_OpenAPI_v2.8.json")); h=File.read("specs/openapi/MULO_Swagger_UI_v2.8.html").split("const spec = ",2).fetch(1).split(";\nwindow.onload",2).fetch(0); puts [y==j,j==JSON.parse(h)].inspect'
  ```

- [ ] **Step 5: 세 산출물을 커밋한다.** 세 v2.8 파일만 `git add`하고 `git commit -m 'docs: OpenAPI 매칭 1대1 예시 동기화 #207'`.

### Task 3: 적용 조건과 미해결 구조 차이 기록

**Files:**
- Create: `docs/one-to-one-match-notification-rollout-verification.md`
- Read: `src/main/resources/db/migration/V6__separate_matching_and_chat_schema.sql:6-17,26-59`
- Read: `src/main/resources/db/migration/V7__prepare_notification_outbox_schema.sql:1-29`

**Interfaces:**
- Consumes: Tasks 1–2의 변경 문서와 DB별 읽기 권한.
- Produces: 환경별 0건 확인 상태, 미확인 항목, 후속 구현의 배포 차단 조건을 분리한 기록.

- [ ] **Step 1: 로컬 Backend DB를 읽기 전용으로 재확인한다.** 비밀번호를 출력하지 않는 기존 로컬 접속 방식으로 `SELECT COUNT(*) FROM matches`, `SELECT status, COUNT(*) FROM match_requests GROUP BY status`, `SELECT COUNT(*) FROM notifications WHERE type='MATCH_COMPLETED'`를 실행한다. `information_schema.tables`에서 Outbox 후보 테이블 존재를 먼저 확인하고, 존재할 때만 `MATCH_COMPLETED` 이벤트 수를 조회한다. 조회 시간·환경·실행 SQL·결과를 기록한다.
- [ ] **Step 2: 다른 적용 환경과 별도 Chat DB의 확인 상태를 기록한다.** 환경별 Backend `matches`·`match_requests`·`MATCH_COMPLETED` Outbox/알림, Chat DB 채팅방·참가자 이력의 읽기 전용 조회 결과를 적는다. Chat DB 스키마가 현재 작업 공간에 없으므로 해당 소유 스키마를 먼저 확인해 쿼리를 작성한다. 결과를 확인하지 못한 환경은 `미확인`, 4인 이력이 있거나 권한이 없으면 `배포 보류`로 적고 0건으로 추정하지 않는다.
- [ ] **Step 3: 구조 차이와 배포 조건을 적는다.** V6의 실제 `MATCHING`, `matches.match_id`, Chat DB 소유 `chat_room_id`와 현재 정책/테이블/OpenAPI의 V1식 상태·FK 설명 차이를 파일/행으로 기록한다. 두 DB의 참가자 등록 완료 확인 방식이 확정되기 전 `MATCH_COMPLETED` 발행 구현을 시작하지 않는 조건을 명시한다. 과거 4인 이력 발견 시 삭제·변환 없이 보존/호환 정책을 재논의한다.
- [ ] **Step 4: 최종 문서 검증을 한다.** 기록에 모든 적용 환경의 확인/미확인 상태와 SQL·시각이 있는지 확인하고 `git diff --check`를 통과시킨다. 로컬 확인만으로 다른 DB 0건을 주장하지 않는다.
- [ ] **Step 5: 검증 기록을 커밋한다.** 새 파일만 `git add`하고 `git commit -m 'docs: 1대1 매칭 알림 적용 조건 기록 #207'`.

## Execution Boundary

이 계획의 완료는 **문서 반영 완료**를 뜻한다. 매칭·채팅 Java 코드, Outbox 발행/처리, cross-DB 완료 프로토콜, 전체 환경 데이터 확인 및 배포 완료를 뜻하지 않는다. 다른 환경에 대한 접근 정보가 없는 상태에서는 Task 3에 `미확인`을 남기고 1:1 전용 구현·배포 조건을 충족했다고 표시하지 않는다.
