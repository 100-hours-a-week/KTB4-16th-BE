# Monthly Report AI Callback Design

## Goal

기존 Mock AI 기반 월간 리포트 생성을 실제 AI의 비동기 배치 생성 및 결과 포함 콜백 방식으로 전환한다.

## Confirmed Requirements

- 관련 이슈 번호는 `#130`을 유지한다.
- 백엔드는 `POST /api/reports/batch-generate`에 `year`, `month`, `userIds`를 보내고 `X-Internal-Token`을 사용한다.
- AI의 정상 접수 응답은 `202 Accepted`와 `status: QUEUED`, `jobId`, `targetCount`다.
- 백엔드는 AI 요청 전에 사용자별 월간 집계 스냅샷을 `PENDING`으로 저장한다.
- AI 콜백은 사용자별 결과를 모두 포함한다. 성공은 `COMPLETED`, 실패는 `FAILED`다.
- `COMPLETED` 결과는 `aiRecap.text`와 `photoScenes`를 포함한다. `photoScenes`는 빈 배열을 허용한다.
- `aiRecap.text`는 한글 기준 100자 이하여야 한다. 초과하면 콜백 전체를 `502 AI_SERVICE_ERROR`로 거절하고 DB를 변경하지 않는다.
- 콜백의 일반 형식 오류 또는 대상 리포트 불일치는 콜백 전체를 `400 INVALID_INPUT`으로 거절하고 DB를 변경하지 않는다.
- 같은 성공 콜백 재전송은 기존 사진 장면 통계를 교체하는 멱등 처리로 지원한다.
- 이미 `COMPLETED`인 리포트에 다시 도착한 `FAILED` 결과는 기존 완료 데이터를 유지한다.

## Existing Constraints

- `monthly_reports`는 사용자·연·월 유일 제약과 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` 상태를 이미 가진다.
- `ai_recap_text`는 `VARCHAR(100)`이다.
- `monthly_photo_scene_stats`는 `(monthly_report_id, scene_tag)` 유일 제약, `count >= 0`, `ratio 0..100` 제약을 가진다.
- 외부 HTTP 호출은 DB 트랜잭션 밖에서 완료한다.
- DB schema와 Flyway migration은 변경하지 않는다.
- `AI_BASE_URL`, `AI_INTERNAL_TOKEN`은 환경 변수로만 주입하며 실제 값은 코드·문서·로그에 기록하지 않는다.

## Architecture

### Dispatch Flow

1. `MonthlyReportScheduler`가 매월 1일 01:00 KST에 지난달 생성을 시작한다.
2. `MonthlyReportPreparationService`가 활동 사용자별 집계, 대표 장소, 대표 아티스트, 평균 기분을 저장하고 리포트 상태를 `PENDING`으로 만든다.
3. 준비된 사용자 ID 목록을 `MonthlyReportBatchAiClient`가 AI Gateway에 요청한다. HTTP 요청은 preparation 트랜잭션이 끝난 뒤 실행한다.
4. AI가 `202 QUEUED`를 반환하면 해당 `PENDING` 리포트를 `PROCESSING`으로 전환한다.
5. AI 요청 실패·비정상 응답이면 해당 `PENDING` 리포트를 `FAILED`로 전환한다. 리포트 집계 스냅샷은 보존한다.

### Callback Flow

1. AI가 `POST /internal/ai/report-ready`로 완료 결과를 보낸다.
2. `AiInternalTokenFilter`는 정확히 이 경로에서만 `X-Internal-Token`을 검증한다. 누락·불일치는 `401 UNAUTHORIZED`다.
3. `MonthlyReportAiCallbackController`는 JSON 형식을 검증하고 `MonthlyReportAiCallbackService`에 전달한다.
4. 서비스는 콜백 전체를 사전 검증한다. 일반 입력 오류는 `400 INVALID_INPUT`, 100자 초과 회고는 `502 AI_SERVICE_ERROR`다. 둘 다 DB를 바꾸지 않는다.
5. 검증 뒤 하나의 트랜잭션으로 사용자별 리포트를 갱신한다.
   - `COMPLETED`: 회고 문장을 저장하고 기존 장면 통계를 삭제한 뒤 새 장면 통계를 저장하고 상태를 `COMPLETED`로 바꾼다.
   - `FAILED`: 아직 완료되지 않은 리포트만 `FAILED`로 바꾼다. 이미 완료된 리포트의 결과는 유지한다.
6. 성공 시 `204 No Content`를 반환한다.

## Internal Callback Contract

```http
POST /internal/ai/report-ready
X-Internal-Token: <AI_INTERNAL_TOKEN>
Content-Type: application/json
```

```json
{
  "jobId": "report_batch_2026-9",
  "year": 2026,
  "month": 9,
  "generatedAt": "2026-10-01T03:12:00+09:00",
  "results": [
    {
      "userId": 7,
      "status": "COMPLETED",
      "aiRecap": { "text": "9월은 산책과 음악으로 채운 달이었어요." },
      "photoScenes": [
        { "tag": "일상", "count": 3, "ratio": 60 }
      ]
    },
    {
      "userId": 12,
      "status": "FAILED",
      "errorCode": "UPSTREAM_UNAVAILABLE"
    }
  ]
}
```

### Validation

| Field | Rule |
| --- | --- |
| `jobId` | non-blank |
| `year` | integer, 2026 or later |
| `month` | integer, 1 through 12 |
| `generatedAt` | ISO-8601 offset datetime |
| `results` | non-empty; each requested user has exactly one result |
| `results[].userId` | positive `Long` |
| `results[].status` | `COMPLETED` or `FAILED` |
| completed `aiRecap.text` | non-blank, maximum 100 Korean characters |
| completed `photoScenes` | empty array allowed |
| `photoScenes[].tag` | non-blank, maximum 50 characters |
| `photoScenes[].count` | integer, zero or greater |
| `photoScenes[].ratio` | integer, 0 through 100 |
| failed `errorCode` | non-blank |

## Security

- `/internal/ai/report-ready`는 브라우저 JWT와 CSRF 토큰을 받지 않는 서버 간 호출이다.
- Spring Security에서 정확히 이 POST 경로만 CSRF 검증과 JWT 인증 대상에서 제외한다.
- 해당 예외는 공개 접근 허용이 아니라 `AiInternalTokenFilter`의 `X-Internal-Token` 검증을 전제로 한다.
- 다른 `/internal/**` 및 `/api/**` 경로의 기존 접근 규칙은 변경하지 않는다.

## Persistence and Idempotency

- AI 요청 직전 생성한 집계 스냅샷이 콜백 대상의 식별 기준이다: `(userId, year, month)`.
- 콜백 `jobId`는 요청·응답 상관관계 및 로그 식별자로만 사용하며 DB에 저장하지 않는다. 스키마 변경은 하지 않는다.
- `COMPLETED` 재전송은 AI 소유 데이터인 회고와 사진 장면 통계를 교체하므로 장면 행이 중복되지 않는다.
- `FAILED` 재전송은 미완료 리포트를 `FAILED`로 유지하며 완료된 리포트를 되돌리지 않는다.

## Error Contract

| Condition | Response | Persistence |
| --- | --- | --- |
| missing or invalid internal token | 401 `UNAUTHORIZED` | none |
| invalid callback field or unmatched snapshot | 400 `INVALID_INPUT` | none |
| recap longer than 100 characters | 502 `AI_SERVICE_ERROR` | none |
| valid callback | 204 No Content | all validated results applied |
| batch request network or non-202 response | scheduler internal failure | prepared reports become `FAILED` |

## Testing Strategy

- `MonthlyReportBatchAiClient` tests: request URL, token header, JSON body, `202` mapping, non-202 and malformed response failures.
- preparation tests: active-user snapshots become `PENDING`; duplicate period is skipped.
- dispatch tests: `202` changes prepared reports to `PROCESSING`; AI request failure changes them to `FAILED`.
- callback service tests: mixed completed/failed results, scene replacement on duplicate completed callback, completed report protection from late failed callback.
- callback validation tests: generic malformed input rollback, overlong recap 502 rollback, scene bounds, unknown snapshot.
- security/controller tests: missing or invalid token 401, valid internal callback 204, exact callback route CSRF behavior, unrelated internal path remains denied.
- final verification: focused report tests, `./gradlew checkstyleMain`, then `.env` exported `./gradlew test`.
