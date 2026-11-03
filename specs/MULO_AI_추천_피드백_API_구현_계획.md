# MULO AI 추천 피드백 API 구현 계획

> 상태: 구현 완료. 최초 계획 당시에는 신규 API와 응답 변경을 미구현으로 보았으며, 현재는 아래의 구현 완료 계약을 기준으로 한다.
> 정책 기준: `MULO 서비스 정책 정의서 v2.1`의 AI 추천 피드백 정책 및 BE PR #1의 Flyway V13 스키마.

## 1. 현재 제공 계약

| API | 현재 동작 |
| --- | --- |
| `POST /api/recommendations/photo` | 요청 body `{uploadId}`. 성공 시 정확히 5곡과 최상위 `recommendationId`를 `{message, recommendationId, recommendReason, data}`로 반환한다. |
| `POST /api/recommendations/playlists` | `recommendationAttempt`(1~3)를 받으며 생략·null은 1회차로 처리한다. 정상 생성은 201 및 최상위 `recommendationId`, `data.replaced=true`; fallback은 200 및 `data.replaced=false`이며 ID는 생략한다. |
| `POST /api/ai-recommendations/{recommendationId}/feedback` | 본인 추천 결과에 대한 인증 피드백 API. 성공 201; 잘못된 요청 400, 소유권 403, 미존재 404, 중복 409. |

## 2. 기존 API 변경 및 현재 계약

### `POST /api/recommendations/photo`

- 요청 body는 필수 `uploadId`(Long)다.
- 정상 추천 시 `ai_recommendation_results`와 전체 곡 목록을 저장하고 최상위 `recommendationId`와 5곡을 반환한다(HTTP 200).
- 사진 피드백은 추천 결과 전체의 LIKE/DISLIKE이며 개별 곡을 평가하거나 선호 곡을 선택하지 않는다.
- `selectedTrackId` 없이 피드백을 제출한다. FE는 정상 결과의 피드백 제출 성공 전 화면 닫기·추천 곡 선택·직접 검색·다음 단계 진행을 막는다.

### `POST /api/recommendations/playlists`

- 요청의 `recommendationAttempt`는 값이 있으면 1~3만 허용하고 생략·null은 1로 처리한다. 시트 세션 회차는 FE가 관리한다.
- 정상 추천 요청은 회차별 결과와 전체 곡 목록을 저장하고 HTTP 201, 최상위 `recommendationId`, `data.replaced=true`를 반환한다.
- AI가 정확히 5곡인 degraded 결과를 주면 HTTP 200, `data.playlist=null`, `data.replaced=false`, `recommendReason=null`을 반환한다. 프론트가 자신의 화면 목록을 유지한다. 새 이력을 저장하지 않으며 `recommendationId`는 JSON에서 생략한다.
- FE는 피드백 제출 전·제출 중 재추천 및 닫기를 막는다. 실패/fallback은 회차에 포함하지 않으며 시트를 닫았다 다시 열면 회차가 초기화된다.

### 화면 복원 제거 (2026-11-02)

- GET과 recommendationPlaylistId는 제거한다. 평가용 recommendationId와 음악 카탈로그 ID는 유지한다.
- 두 POST 응답은 AI의 recommendReason을 그대로 전달하고 누락은 null로 반환한다. 추천 이유는 저장하지 않는다.
- 화면 상태는 FE가 소유하며 복원용 두 테이블만 Flyway V14에서 삭제한다.

## 3. 피드백 제출 API

### `POST /api/ai-recommendations/{recommendationId}/feedback`

요청 필드:

| 필드 | 타입 | 조건 |
| --- | --- | --- |
| `feedback` | `LIKE` 또는 `DISLIKE` | 필수 |
| `selectedTrackId` | 최대 22자의 Spotify Track ID 문자열 또는 null/생략 | PLAYLIST + LIKE에서 필수. PHOTO 및 DISLIKE에는 없어야 한다. |

검증 정책:

- 인증된 사용자만 제출할 수 있다.
- 인증 사용자가 소유한 추천 결과만 평가할 수 있다.
- 추천 결과당 피드백을 최대 한 번 저장한다. 중복 및 동시 중복 제출은 409로 처리한다.
- PLAYLIST + LIKE에는 추천 결과 곡 목록에 포함된 곡을 정확히 하나 선택해야 한다.
- PHOTO 및 DISLIKE에는 `selectedTrackId`가 없어야 한다.
- 사용자가 제출하지 않은 피드백은 행으로 저장하지 않는다.
- 성공 응답은 201이며 `{message, data:{feedbackId, recommendationId, feedback, selectedTrackId?}}` 구조다. 선택 곡이 없으면 응답 JSON에서 `data.selectedTrackId`는 생략된다.
- 오류 상태는 입력 오류 400, 인증 실패 401, 소유권 오류 403, 추천 결과 미존재 404, 중복 제출 409다.

## 4. 저장 스키마

Flyway V13의 실제 컬럼 및 제약은 [테이블 정의서](MULO_테이블_정의서_v8.1.md)의 §29~31에 기록되어 있다.

- 추천 성공 시 결과와 곡 스냅샷을 저장한다. 결과 곡에는 Spotify Track ID와 추천 순서를 보존한다.
- 피드백 행은 사용자가 제출한 경우에만 저장한다.
- 추천 이력과 피드백은 보존한다. 화면 복원용 `recommendation_playlists` / `recommendation_playlist_items`만 V14에서 삭제한다.
- `selected_track_id`의 조건부 필수 여부와 추천 목록 포함 여부는 DB CHECK가 아니라 Backend 서비스가 검증한다.

## 5. 최초 계획의 미확정 사항과 결정 결과

| 최초 미확정 항목 | 결정 결과 |
| --- | --- |
| 추천 회차 전달 | `POST /api/recommendations/playlists`의 `recommendationAttempt` 정수 필드로 전달하며 값이 있으면 1~3을 검증하고 생략·null은 1로 처리한다. 회차 관리는 FE가 맡는다. |
| 현재 플레이리스트 연결 | 2026-11-02 사용자 확정으로 GET과 recommendationPlaylistId를 제거한다. 평가용 recommendationId는 유지한다. |
| degraded/fallback | 서버 복원 없이 프론트의 현재 화면 목록 유지, 새 결과 이력 미저장, 응답의 `recommendationId` 생략으로 확정했다. |
| 피드백 API 응답 | 성공 201 및 `message`, `data.feedbackId`, `data.recommendationId`, `data.feedback`, 선택적 `data.selectedTrackId`로 확정했다. |
| 피드백 오류 | 400 입력, 401 인증, 403 소유권, 404 미존재, 409 중복 제출을 적용한다. |

## 6. FE 진행 정책과 Backend 강제 범위

- 정상 추천 결과에 피드백이 없으면 FE는 사진 추천의 닫기·곡 선택·직접 검색·다음 단계 진행과 플레이리스트의 닫기·재추천을 차단한다. 제출 중에도 제한하며, 제출 실패 시 재시도를 허용한다.
- 추천 실패·결과 없음·정상 결과 ID 누락은 FE가 피드백 제출을 요구하지 않고 닫기를 허용한다.
- 플레이리스트 시트당 성공한 정상 추천은 최대 3회다. 실패와 fallback은 회차에 포함되지 않고, 시트 재오픈 시 초기화한다.
- Backend는 피드백 값·소유권·선택 곡 조건·중복 제출을 검증하고 `recommendationAttempt`의 1~3 범위만 검증한다.
- Backend는 미제출 피드백을 이유로 다음 추천 API 또는 자물쇠 생성 API 호출을 차단하지 않는다. 시트 세션의 회차·3회 제한도 서버에서 독립 강제하지 않는다.

세션과 자물쇠 생성 간의 서버 강제가 후속 요구가 되면 추천 결과와 자물쇠 생성 관계 및 세션 상태 저장을 별도로 설계해야 한다. 현재 스키마에는 그 관계가 없다.
