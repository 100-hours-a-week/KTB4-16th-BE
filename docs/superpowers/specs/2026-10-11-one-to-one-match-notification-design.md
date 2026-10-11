# 1:1 매칭 알림 계약과 관련 문서 반영 설계

## 목적과 범위

신규 매칭을 같은 `vote_question_id`의 서로 다른 선택지에서 A 1명+B 1명으로 구성한다는 사용자 결정을 `MATCH_COMPLETED` 알림 설계와 관련 정책·테이블·OpenAPI 문서에 반영한다. 현재 알림은 첫 구현 단계이므로 신규 이벤트는 2명에게만 발행한다. 기존 4인 매칭·채팅방·Outbox 이력이 **모든 적용 환경에서 0건임을 확인한 후** 1:1 전용 계약을 적용한다. 이 문서는 매칭·채팅 구현이나 배포 자체를 포함하지 않는다.

알림 V7 스키마는 수신자 수를 제한하지 않으므로 변경하지 않는다. 기존 V1~V7 Flyway 파일도 수정하지 않는다. 이 설계는 사용자가 첨부한 MULO V2 알림 통합 프롬프트 §3.1·§5.2의 “이벤트 1건, 수신자별 알림 1건” 원칙과 사용자 최신 결정을 함께 따른다. 첨부 프롬프트의 4인 “현재”, 2인 “향후” 표현은 이번 결정으로 대체한다.

## 확인된 현재 상태

| 근거 | 현재 내용 | 이 설계에 미치는 영향 |
|---|---|---|
| `specs/MULO_서비스_정책_정의서_v2.1.md:281,298-300` | A2+B2, 4인 방·참여자·전원 퇴장 | 1:1 결정과 충돌하므로 관련 문구를 바꿔야 한다. |
| `specs/MULO_테이블_정의서_v8.1.md:757,792,823,841-911` | 투표·매칭·채팅 설명과 예시가 4인을 전제 | 2인 관계와 수명 설명을 맞춰야 한다. |
| `specs/openapi/MULO_OpenAPI_v2.8.yaml:4518,4670,4915-4923,5168` | 4인 매칭 설명, 4명 참여자 예시·퇴장 설명 | YAML·JSON·Swagger HTML 세 파일을 함께 갱신해야 한다. |
| `src/main/resources/db/migration/V6__separate_matching_and_chat_schema.sql:6-17,36-59` | Backend의 `matches`, `MATCHING` 상태, 별도 Chat DB ID; Chat 테이블과 cross-DB FK 제거 | 채팅 생성·참여자 등록과 Backend Outbox INSERT를 단일 DB 트랜잭션이라고 기술할 수 없다. |
| `src/main/resources/db/migration/V7__prepare_notification_outbox_schema.sql:1-29` | 알림 4종과 이벤트-수신자 UNIQUE, `chat_room_id` 논리 참조 | 2명 알림을 위해 추가 DDL이 필요하지 않다. |
| 2026-10-11 로컬 MySQL | `matches` 0건, `matches.chat_room_id IS NOT NULL` 0건; `match_requests` 상태별 조회 결과 없음 | 로컬에 기존 매칭 이력이 없다는 것만 증명한다. 다른 환경·Chat DB는 미확인이다. |

현재 백엔드 Java 소스에는 매칭·채팅·알림 Controller/서비스가 없다. 따라서 이번 반영은 실제 알림 발행 동작을 변경하는 코드 수정이 아니라, 후속 구현의 입력 계약을 고정하는 작업이다.

## 기존 계약과 변경 계약

| 항목 | 기존 문서·프롬프트 | 변경 계약 | 근거 |
|---|---|---|---|
| 매칭 구성 | A2+B2, 총 4명 | 같은 질문의 A1+B1, 총 2명 | 사용자 확정 2026-10-11 |
| Outbox/알림 수 | `MATCH_COMPLETED` Outbox 1건, 알림 4건 예시 | Outbox 1건, 서로 다른 수신자 알림 2건 | 통합 프롬프트 §3.1 + 사용자 1:1 결정 |
| Payload v1 | `matchParticipantCount: 4`, `recipientUserIds` 4개 | `matchParticipantCount: 2`, 중복 없는 `recipientUserIds` 정확히 2개 | 통합 프롬프트 §5.2의 2인 예시 |
| 대상 | `chatRoomId` | `chatRoomId` 유지 | 통합 프롬프트 §5.2·§7.2 |
| 이벤트 발행 시점 | 채팅방·참가자 등록 완료 | 2명 참가자 등록 완료를 확인한 뒤 발행 | 통합 프롬프트 §5.2의 완료 시점 정책 |
| 과거 4인 이벤트 | 4인 재처리 호환 가능성 언급 | 모든 환경에서 4인 이력 0건 확인 후 1:1만 지원 | 사용자 확정 2026-10-11 |

신규 Payload 형식은 아래와 같다. 사용자 ID는 예시이며 A/B 표시는 Payload에 새 필드를 추가한다는 뜻이 아니다.

```json
{
  "payloadVersion": 1,
  "chatRoomId": 502,
  "matchParticipantCount": 2,
  "recipientUserIds": [11, 22]
}
```

발행 측은 같은 질문의 서로 다른 선택지에서 각각 1명인지, ID 2개가 양의 정수·서로 다른 값인지, 채팅방 참가자 등록이 두 명 모두 완료됐는지 확인한다. 알림 처리 측은 보존된 Payload만으로 `payloadVersion`, 필수 필드·자료형, `matchParticipantCount = 2`, 수신자 2명·중복 없음, `chatRoomId` 양의 정수를 재검증한다. Payload에 선택지 스냅샷을 새로 추가하지 않으므로 처리 측이 A/B 구성 자체를 재검증한다고 주장하지 않는다. 알림 2건 저장과 Outbox `COMPLETED` 변경은 통합 프롬프트 §3.2에 따라 한 처리 트랜잭션에서 함께 수행한다.

## 데이터 확인과 적용 조건

1. Backend 적용 대상 DB마다 `matches`, `match_requests`의 이력 및 `MATCH_COMPLETED` Outbox/알림 유무를 읽기 전용으로 확인한다. Outbox 테이블이 실제 존재하는지는 먼저 `information_schema`로 확인한다.
2. 별도 Chat DB마다 채팅방과 참가자 이력을 확인한다. 현재 작업 공간에는 Chat DB 스키마·접근 정보가 없으므로 구체 SQL은 해당 소유 저장소/DB 구조를 확인한 뒤 작성한다.
3. 어느 환경이든 4인 이력이 있거나 조회 권한이 없다면 1:1 전용 처리기 배포를 보류한다. 과거 행을 삭제·변환하지 않고 사용자와 보존·호환 정책을 다시 결정한다.
4. 0건 확인 결과는 환경, 조회 시각, 사용한 읽기 전용 SQL과 함께 기록한다. 로컬 0건을 다른 DB의 0건으로 대체하지 않는다.

## 문서 반영 경계와 검증

구현 계획에서는 정책서의 `MATCH-001`, `CHAT-001`~`CHAT-003`과 4인 전제를 가진 문구, 테이블 정의서의 §17~20 관련 4인 설명, OpenAPI v2.8 YAML·JSON·Swagger HTML의 매칭 생성·상태 조회·채팅방 멤버 예시·퇴장 설명을 정확히 찾아 변경한다. 원본과 수정 문구를 diff로 검토하고 세 API 산출물의 의미가 같은지 확인한다. API 경로·필드·상태코드와 V7 스키마는 이 작업에서 변경하지 않는다. v2.7 파일은 과거 버전으로 보존한다.

V6 이후의 `MATCHING` 상태와 Chat DB 분리 구조가 현행 문서의 V1 설명과 다른 점은 별도 정합성 문제로 기록한다. 1:1 숫자 변경을 이유로 매칭 상태, cross-DB 완료 프로토콜, Chat DB 스키마를 추정해 바꾸지 않는다. `MATCH_COMPLETED` Outbox 발행 구현은 실제 채팅방 생성·두 참가자 등록 완료를 신뢰할 수 있게 확인하는 프로토콜 설계가 선행돼야 한다.
