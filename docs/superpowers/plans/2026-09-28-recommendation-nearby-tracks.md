# AI 추천 플레이리스트 주변 자물쇠 문맥 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 지도에서 사용자가 선택한 인기 장소·클러스터의 최근 인기 자물쇠 음악 Top 5를 AI 추천의 선택 `nearbyTracks` 문맥으로 전달한다.

**Architecture:** 프론트는 인기 마커 또는 클러스터 선택 상태의 `placeIds`를 HomePage로 올려 추천 생성 요청에 선택적으로 전달한다. 백엔드는 추천 모듈의 읽기 전용 조회 서비스에서 기존 `RecordRepository.findPopularTrackAggregates`를 재사용해 최근 7일 Top 5를 만든다. Recommendation AI Client는 비어 있지 않은 목록만 JSON `nearbyTracks` 필드로 직렬화한다.

**Tech Stack:** React, TypeScript, Vitest, Java 21, Spring Boot, Spring Data JPA, Jackson, JUnit 5, Mockito, MockRestServiceServer.

**Spec:** [AI 추천 플레이리스트 주변 자물쇠 문맥 설계](../specs/2026-09-28-recommendation-nearby-tracks-design.md)

## Global Constraints

- AI 요청의 `place` 객체, 법정동 코드·이름, `requestId`는 이번 범위에서 전송하지 않는다.
- 프론트는 인기 마커 또는 클러스터를 선택했을 때만 `placeIds`를 추천 생성 API 요청에 포함한다. 선택이 없으면 빈 배열 대신 필드를 생략한다.
- `placeIds`는 선택 입력이며, 전달됐을 때만 양의 정수 ID 목록으로 검증한다.
- 집계 대상은 전달된 모든 `placeIds`의 최근 7일 이내, 삭제되지 않은 자물쇠다.
- 집계 정렬은 음악별 자물쇠 수 내림차순, 동률이면 가장 최근 자물쇠 생성 시각 내림차순, 이후 음악 ID 오름차순이다.
- AI에는 정렬 결과 중 최대 5곡만 `nearbyTracks`로 전달한다. 결과가 비면 필드를 생략한다.
- 기존 필수 AI 문맥인 `userId`, `weather.condition`, `localTime`과 현재 `weather.temperature`, `limit: 10` 동작을 유지한다.
- 날씨 조회 실패는 AI 호출 전 `502 WEATHER_API_ERROR`로 중단하고, AI `degraded=true` 또는 빈 곡 목록이면 기존 플레이리스트를 유지한다.
- 새 의존성, Flyway 마이그레이션, DB 스키마 변경은 하지 않는다.

## Review Focus

- 인기 마커 또는 클러스터를 해제한 뒤 새 추천을 만들면 이전 `placeIds`가 요청에 남지 않아야 한다.
- `placeIds`가 없거나 최근 7일 집계가 비어 있어도 AI 추천 요청은 `nearbyTracks` 없이 정상 생성되어야 한다.
- `placeIds`에 0 또는 음수가 있으면 백엔드는 AI·DB 조회 전에 입력 검증 오류로 거부해야 한다.
- 동일 집계 수의 음악은 최신 자물쇠 생성 시각과 음악 ID 순서가 기존 인기 음악 API와 동일해야 한다.
- 날씨 조회가 실패하면 `placeIds`가 있더라도 인기 음악 조회와 AI 호출을 시작하지 않아야 한다.

---

### Task 1: 백엔드 선택 `placeIds` 계약과 주변 음악 조회

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/dto/request/CreateRecommendationPlaylistRequest.java:7-13`
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/client/RecommendationAiClient.java:8-16`
- Create: `src/main/java/com/ktb4/team16/mulo/recommendation/service/RecommendationNearbyTrackQueryService.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/service/RecommendationPlaylistCommandService.java:14-35`
- Test: `src/test/java/com/ktb4/team16/mulo/recommendation/service/RecommendationNearbyTrackQueryServiceTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/recommendation/service/RecommendationPlaylistCommandServiceTest.java:32-138`
- Modify: `src/test/java/com/ktb4/team16/mulo/recommendation/controller/RecommendationPlaylistControllerTest.java`

**Interfaces:**
- Consumes: optional `List<Long> placeIds` from `CreateRecommendationPlaylistRequest`; `RecordRepository.findPopularTrackAggregates(List<Long>, LocalDateTime)`; application `Clock`.
- Produces: `RecommendationNearbyTrackQueryService.findTopTracks(List<Long> placeIds): List<RecommendationAiClient.NearbyTrack>` and `RecommendationContext(Long userId, WeatherCondition weatherCondition, BigDecimal temperature, OffsetDateTime requestedAt, List<NearbyTrack> nearbyTracks)`.

- [ ] **Step 1: Write the failing tests**

Add `RecommendationNearbyTrackQueryServiceTest` proving that it calls `findPopularTrackAggregates` with `LocalDateTime.now(clock).minusDays(7)`, maps title/artist/count, and retains only the first five ordered aggregates. In command-service tests, prove that non-empty optional `placeIds` are converted to `NearbyTrack` values before the AI Client call, no IDs produce an empty context list, and weather failure calls neither the query service nor the AI Client. In controller tests, prove `placeIds: [10, 20]` binds and `placeIds: [0]` is rejected as a validation error.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests '*RecommendationNearbyTrackQueryServiceTest' --tests '*RecommendationPlaylistCommandServiceTest' --tests '*RecommendationPlaylistControllerTest'`

Expected: FAIL because the request, recommendation context, and nearby-track query service do not exist.

- [ ] **Step 3: Implement the request, query, and orchestration changes**

Add nullable `List<@Positive Long> placeIds` to `CreateRecommendationPlaylistRequest`. Add `record NearbyTrack(String title, String artistName, Long count)` and the `nearbyTracks` list to `RecommendationAiClient.RecommendationContext`. Implement `RecommendationNearbyTrackQueryService.findTopTracks(List<Long>)` as a read-only service: return an empty list for `null` or empty input; otherwise query the existing repository with the seven-day cutoff and map only five results. Inject it into `RecommendationPlaylistCommandService`; after successful weather retrieval, create the nearby-track list and include it in the AI context.

Keep the query in the recommendation module; do not make an HTTP call to the existing popular-tracks endpoint.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests '*RecommendationNearbyTrackQueryServiceTest' --tests '*RecommendationPlaylistCommandServiceTest' --tests '*RecommendationPlaylistControllerTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

Stage the Task 1 backend source and tests, then commit with `feat: 추천 주변 자물쇠 문맥 추가 #130`.

### Task 2: AI HTTP JSON의 선택 `nearbyTracks` 직렬화

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/client/ContextRecommendationAiClient.java:33-90`
- Modify: `src/test/java/com/ktb4/team16/mulo/recommendation/client/ContextRecommendationAiClientTest.java:25-144`

**Interfaces:**
- Consumes: `RecommendationContext.nearbyTracks()` from Task 1.
- Produces: AI JSON에서 목록이 비어 있지 않을 때만 `nearbyTracks: [{ title, artistName, count }]`를 포함하는 `POST /api/context-recommend` 요청.

- [ ] **Step 1: Write the failing HTTP boundary tests**

Extend `ContextRecommendationAiClientTest` with one context containing two nearby tracks and assert the exact nested `nearbyTracks` JSON array. Add a context with an empty nearby-track list and assert that the JSON has no `nearbyTracks` property while the existing required fields and `limit: 10` remain unchanged.

- [ ] **Step 2: Run the client tests to verify they fail**

Run: `./gradlew test --tests '*ContextRecommendationAiClientTest'`

Expected: FAIL because the AI request record has no `nearbyTracks` field.

- [ ] **Step 3: Implement optional JSON serialization**

Extend the private `ContextRecommendationAiRequest` record with the nearby-track list and add a private JSON DTO that exposes only `title`, `artistName`, and `count`. Use Jackson's non-empty inclusion annotation on this property so an empty list is omitted, not emitted as `[]`. Preserve the `X-Internal-Token`, URL, timeout, response validation, and error conversion behavior.

- [ ] **Step 4: Run the client tests to verify they pass**

Run: `./gradlew test --tests '*ContextRecommendationAiClientTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

Stage the Task 2 AI Client source and tests, then commit with `feat: AI 요청에 주변 인기 음악 전달 #130`.

### Task 3: 홈 지도 선택 상태를 추천 생성 요청에 연결

**Files:**
- Modify: `src/features/home-map/ui/HomeMap.tsx:37-41, 214, 556-615`
- Modify: `src/pages/home/ui/HomePage.tsx:11-15, 87-97`
- Modify: `src/features/home-playlist/model/recommendationPlaylist.types.ts:1-5`
- Modify: `src/features/home-playlist/ui/HomePlaylistSheet.tsx:110-138`
- Modify: `src/features/home-playlist/api/recommendationPlaylistApi.ts:19-44`
- Modify: `src/features/home-playlist/api/recommendationPlaylistApi.test.ts:46-87`
- Modify: `src/features/home-playlist/ui/HomePlaylistSheet.test.tsx:84-116`
- Modify: `src/features/home-map/ui/HomeMap.test.tsx`

**Interfaces:**
- Consumes: HomeMap's popular-marker and cluster click `placeIds`.
- Produces: `onPopularPlaceSelectionChanged(placeIds: number[])` callback, HomePage `selectedPopularPlaceIds` state, and `RecommendationCoordinates` with optional `placeIds?: number[]`.

- [ ] **Step 1: Write the failing frontend tests**

Add HomeMap tests that assert a popular marker click reports its one `placeId`, a popular cluster click reports all deduplicated IDs, and clearing the selection reports `[]`. Add API tests proving a non-empty `placeIds` array is serialized with coordinates and an absent/empty list is omitted. Update the playlist-sheet test to prove selected IDs are forwarded during recommendation creation.

- [ ] **Step 2: Run the focused frontend tests to verify they fail**

Run: `npm test -- --run src/features/home-map/ui/HomeMap.test.tsx src/features/home-playlist/api/recommendationPlaylistApi.test.ts src/features/home-playlist/ui/HomePlaylistSheet.test.tsx`

Expected: FAIL because HomeMap exposes no selection callback and the request type only contains coordinates.

- [ ] **Step 3: Implement selection state propagation and conditional request serialization**

Add `onPopularPlaceSelectionChanged` to `HomeMapProps`. Invoke it whenever the popular marker/cluster selection is set and whenever `clearMapSelection` clears it. In HomePage, own the selected ID state and pass it to `HomePlaylistSheet`. Extend its location input with an optional `placeIds` only when the array is non-empty. In `createRecommendationPlaylist`, serialize `{ latitude, longitude, ...(placeIds?.length ? { placeIds } : {}) }` without changing CSRF handling.

- [ ] **Step 4: Run the focused frontend tests to verify they pass**

Run: `npm test -- --run src/features/home-map/ui/HomeMap.test.tsx src/features/home-playlist/api/recommendationPlaylistApi.test.ts src/features/home-playlist/ui/HomePlaylistSheet.test.tsx`

Expected: PASS.

- [ ] **Step 5: Commit**

Stage Task 3 frontend files and tests, then commit with `feat: 지도 선택 장소를 추천 요청에 포함 #130`.

### Task 4: 공개 OpenAPI 계약 및 전체 회귀 검증

**Files:**
- Modify: `specs/openapi/MULO_OpenAPI_v2.7.yaml: POST /api/recommendations/playlists request schema and description`
- Modify: `docs/recommendation-playlist-guide.md` only if its current request example omits the new optional field

**Interfaces:**
- Consumes: implemented optional `placeIds` request contract from Tasks 1–3.
- Produces: API documentation that marks `placeIds` as optional and describes its map-marker/cluster semantics; verified backend and frontend behavior.

- [ ] **Step 1: Update the OpenAPI request contract**

Document `placeIds` as an optional array of positive `int64` values. State that it is sent only for a user-selected popular marker/cluster, is omitted when there is no selection, and is used to build the AI's recent-seven-day `nearbyTracks` Top 5. Do not document `place`, legal-dong fields, or `requestId` as recommendation request fields.

- [ ] **Step 2: Run backend formatting and tests**

Run: `./gradlew checkstyleMain && ./gradlew test`

Expected: PASS.

- [ ] **Step 3: Run frontend tests and production build**

Run: `npm test -- --run && npm run build`

Expected: PASS.

- [ ] **Step 4: Review the final diff for contract scope and secrets**

Run: `git diff --check && git diff -- specs/openapi/MULO_OpenAPI_v2.7.yaml src/main src/test`

Expected: only the optional `placeIds` public request extension, nearby-track lookup/serialization, tests, and documentation; no secret or unrelated changes.

- [ ] **Step 5: Commit**

Stage Task 4 documentation, then commit with `docs: 추천 주변 음악 요청 계약 반영 #130`.

## Self-Review

- **Spec coverage:** Task 1 implements optional request validation, recent-seven-day query reuse, Top 5 mapping, and weather short-circuit. Task 2 handles AI JSON omission. Task 3 provides the only permitted source of `placeIds`: user-selected popular marker/cluster. Task 4 documents the public contract and verifies both projects.
- **Type consistency:** frontend `placeIds?: number[]` becomes JSON `placeIds`, backend receives `List<Long> placeIds`, query returns `List<RecommendationAiClient.NearbyTrack>`, and client serializes it as `nearbyTracks`.
- **Review focus coverage:** Task 3 tests selection clearing and absent serialization; Task 1 tests empty results, invalid IDs, and weather failure; Task 1's mocked ordered aggregates test existing ranking preservation.
- **Scope:** no legal-dong fields, AI `place`, `requestId`, schema migration, dependency, or transaction-boundary change is included.
