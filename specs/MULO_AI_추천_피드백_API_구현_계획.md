# MULO AI 추천 피드백 API 구현 계획

> 상태: 구현 예정. 이 문서는 현재 OpenAPI v2.8 계약을 변경하지 않는다. 아래 응답 변경과 신규 API는 Backend 구현·계약 검토가 끝나기 전까지 제공 중인 API로 간주하지 않는다.
> 정책 기준: `MULO 서비스 정책 정의서 v2.1`의 AI 추천 피드백 정책 및 BE PR #1의 Flyway V13 스키마.

## 1. 현재 제공 계약

| API | 현재 동작 |
| --- | --- |
| `POST /api/recommendations/photo` | 사진 추천 결과 `message`, `data` 반환. `recommendationId`는 아직 반환하지 않는다. |
| `POST /api/recommendations/playlists` | 현재 플레이리스트 생성 또는 기존 목록 유지 결과 반환. `recommendationId`는 아직 반환하지 않는다. |
| `GET /api/recommendations/playlists` | 사용자별 현재 플레이리스트를 조회한다. 플레이리스트 ID는 반환하지만 AI 추천 이력의 `recommendationId` 연결은 없다. |
| `POST /api/ai-recommendations/{recommendationId}/feedback` | 구현되지 않았다. |

## 2. 기존 API 변경 예정

### `POST /api/recommendations/photo`

- 추천 요청이 성공하면 `ai_recommendation_results`와 해당 결과의 전체 곡 목록을 저장한다.
- 성공 응답에 해당 이력의 `recommendationId`를 추가할 예정이다.
- 사진 추천 피드백은 추천 결과 전체에 대한 LIKE/DISLIKE다. 개별 곡 평가는 없고, 선호 곡을 선택하지 않는다.
- 추천 피드백 제출은 선택 사항이며 제출하지 않거나 화면을 닫으면 피드백 행을 만들지 않는다.

### `POST /api/recommendations/playlists`

- 추천 요청이 성공하면 회차별 추천 결과와 전체 곡 목록을 별도 저장한다.
- 성공 응답에 해당 이력의 `recommendationId`를 추가할 예정이다.
- 추천 회차를 Backend에 전달하는 구체적인 DTO 방식은 구현 전에 확정한다.
- degraded/fallback으로 기존 목록을 유지하는 경우 추천 결과를 저장할지와 그 응답의 `recommendationId`를 생략하거나 기존 ID로 채울지 모두 미확정이다.

### `GET /api/recommendations/playlists`

- 기존 API는 현재 플레이리스트 리소스 조회로 유지한다.
- 응답에 AI 추천 이력 `recommendationId`를 포함할 필요가 있는지 검토한다.
- 플레이리스트와 신규 추천 이력을 어떤 키 또는 관계로 연결할지는 미확정이다. `recommendationPlaylistId`를 `recommendationId`로 간주하지 않는다.

## 3. 신규 API 구현 예정

### `POST /api/ai-recommendations/{recommendationId}/feedback`

요청 필드 계획:

| 필드 | 타입 | 조건 |
| --- | --- | --- |
| `feedback` | `LIKE` 또는 `DISLIKE` | 필수 |
| `selectedTrackId` | Spotify Track ID 문자열 또는 null/생략 | PLAYLIST + LIKE에서 필수. 그 외에는 보내지 않는다. |

검증 정책:

- 인증된 사용자만 제출할 수 있다.
- 인증 사용자가 소유한 추천 결과만 평가할 수 있다.
- 추천 결과당 피드백을 최대 한 번 저장한다. 중복 제출은 거부한다.
- PLAYLIST + LIKE에는 곡을 정확히 하나 선택해야 한다.
- 선택 곡은 해당 추천 결과의 `ai_recommendation_result_tracks`에 포함되어야 한다.
- PHOTO 및 DISLIKE에는 `selectedTrackId`가 없어야 한다.
- 사용자가 제출하지 않은 피드백은 행으로 저장하지 않는다.

최종 HTTP 상태 코드, 오류 코드, 오류 응답 본문은 아직 정하지 않았다.

## 4. 저장 스키마

Flyway V13의 실제 컬럼 및 제약은 [테이블 정의서](MULO_테이블_정의서_v8.1.md)의 §29~31에 기록되어 있다.

- 추천 성공 시 결과와 곡 스냅샷을 저장한다. 결과 곡에는 Spotify Track ID와 추천 순서를 보존한다.
- 피드백 행은 사용자가 제출한 경우에만 저장한다.
- 추천 이력과 피드백은 기존 `recommendation_playlists` / `recommendation_playlist_items` 교체와 독립적으로 보존한다.
- `selected_track_id`의 조건부 필수 여부와 추천 목록 포함 여부는 DB CHECK가 아니라 Backend 서비스가 검증한다.

## 5. 구현 전 미확정 사항

| 항목 | 결정 필요 내용 |
| --- | --- |
| 추천 회차 전달 | `POST /api/recommendations/playlists`의 DTO 필드·검증 방식 |
| 현재 플레이리스트 연결 | `GET /api/recommendations/playlists`에 `recommendationId`가 필요한지, 결과 이력과 연결할 방법 |
| degraded/fallback | 추천 결과 저장 여부와 응답의 `recommendationId` 처리 |
| 피드백 API 응답 | 최종 성공 상태 코드와 응답 본문 |
| 피드백 오류 | 인증 외 소유권, 중복 제출, 잘못된 선택 곡 등의 최종 상태 코드와 오류 코드 |
