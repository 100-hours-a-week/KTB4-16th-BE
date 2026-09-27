# AI 상황 맞춤 추천 실연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 추천 플레이리스트 생성 API가 Mock 대신 AI Gateway의 `POST /api/context-recommend`를 호출하고, 안전한 응답만 저장한다.

**Architecture:** `RecommendationAiClient`는 요청 문맥과 `tracks`·`degraded`를 포함한 결과를 제공하는 경계로 유지한다. 실제 `RestClient` 구현은 AI HTTP 계약과 오류 변환을 담당하고, Command Service는 트랜잭션 밖에서 AI를 호출한 후 정상·비어 있지 않은 결과만 Writer에 전달한다. Writer의 사용자 잠금과 교체 트랜잭션은 그대로 둔다.

**Tech Stack:** Java 21, Spring Boot Web MVC `RestClient`, Spring Test `MockRestServiceServer`, JUnit 5, Mockito.

**Spec:** 사용자 제공 `AI API 시트 (1).xlsx` 및 [AI API 계약 정리](../../../../../../app/mulo-be/docs/ai-api-contract.md)

## Global Constraints

- `POST /api/context-recommend`에는 `userId`, `weather.condition`, `weather.temperature`, `localTime`을 camelCase JSON으로 보낸다.
- AI 요청에는 최대 추천 곡 수 `limit: 10`을 명시한다.
- 외부 Gateway 테스트의 `AI_BASE_URL`은 `https://mulostudio.com/ai`로 설정한다. Client가 붙이는 `/api/context-recommend`와 결합해 `/ai/api/context-recommend`가 되어야 하며, Base URL에 `/api`를 포함하지 않는다.
- `localTime`은 KST `OffsetDateTime`으로 `+09:00` 오프셋을 보존한다.
- `AI_MOCK_ENABLED=true`에서는 Mock만, `false`에서는 실제 Client만 Bean으로 등록한다.
- AI HTTP 호출은 DB 트랜잭션 안에서 수행하지 않는다.
- `degraded=true` 또는 `tracks=[]`면 기존 플레이리스트를 삭제·저장·교체하지 않는다.
- `albumImageUrl=null`, 필수 곡 필드 누락, HTTP 400/401/503, 타임아웃, 잘못된 JSON은 세부 정보를 노출하지 않는 `502 AI_SERVICE_ERROR`로 변환한다.
- 교체가 발생하면 `201 Created`, 유지되면 `200 OK`와 기존 플레이리스트(없으면 `playlist: null`)를 반환한다.
- Flyway·스키마·의존성·CI 파일을 변경하지 않는다.
- 커밋·push·PR은 별도 사용자 승인 전까지 수행하지 않는다.

## Review Focus

- Docker healthcheck의 `GET /actuator/health`만 인증 없이 통과하고, 그 밖의 비-API 경로는 계속 차단되는지 Security 통합 테스트로 검증한다.
- `degraded=true`가 실수로 Writer를 호출해 기존 플레이리스트를 삭제하지 않는지 Command Service 테스트로 검증한다.
- 정상 빈 `tracks`가 새 빈 플레이리스트를 만들지 않는지 Command Service 테스트로 검증한다.
- AI 응답의 `albumImageUrl=null`이 DB 제약 위반이 되기 전에 `RecommendationAiException`으로 변환되는지 Client 테스트로 검증한다.
- 실제 요청의 `userId`, enum 문자열 날씨값, KST 오프셋 시간이 명세 JSON으로 직렬화되는지 HTTP 경계 테스트로 검증한다.
- 실제 Client와 Mock Client가 동시에 Spring Bean으로 등록되지 않는지 조건부 Bean 컨텍스트 테스트로 검증한다.

---

### Task 0: Actuator Healthcheck

**Files:**
- Modify: `build.gradle:22-60`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/config/SecurityConfig.java:53-72`
- Modify: `src/test/java/com/ktb4/team16/mulo/global/security/SecurityIntegrationTests.java`

**Interfaces:**
- Consumes: Docker's unauthenticated `GET /actuator/health` healthcheck.
- Produces: Actuator `health` endpoint and a Security rule that permits only its GET request.

- [ ] **Step 1: Write the failing Security integration test**

Add a test-only `/actuator/health` probe and assert that an unauthenticated GET returns `200 OK`; retain the existing test proving another non-API path remains denied.

- [ ] **Step 2: Run the test to verify RED**

Run: `./gradlew test --tests '*SecurityIntegrationTests'`

Expected: FAIL because the current `anyRequest().denyAll()` rejects `/actuator/health` with `401`.

- [ ] **Step 3: Add Actuator and the narrow security exception**

Add `spring-boot-starter-actuator` without an explicit version. Before API authentication rules, permit only `HttpMethod.GET` on `/actuator/health`; do not permit `/actuator/**`.

- [ ] **Step 4: Run the security tests to verify GREEN**

Run: `./gradlew test --tests '*SecurityIntegrationTests' --tests '*ProductionSecurityTests'`

Expected: PASS, with the healthcheck public and `/internal` still denied.

---

### Task 1: AI 추천 경계와 실제 HTTP Client

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/client/RecommendationAiClient.java:8-15`
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/client/MockRecommendationAiClient.java:6-18`
- Create: `src/main/java/com/ktb4/team16/mulo/recommendation/client/ContextRecommendationAiClient.java`
- Create: `src/main/java/com/ktb4/team16/mulo/recommendation/client/RecommendationAiException.java`
- Test: `src/test/java/com/ktb4/team16/mulo/recommendation/client/ContextRecommendationAiClientTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/recommendation/client/MockRecommendationAiClientTest.java:11-20`

**Interfaces:**
- Consumes: `AiProperties`, `WeatherCondition`, Spring `RestClient`.
- Produces: `RecommendationAiClient.recommend(RecommendationContext)` returning `RecommendationResult(List<RecommendedTrack> tracks, boolean degraded)`; `RecommendationContext` includes `Long userId`.

- [ ] **Step 1: Write failing HTTP boundary tests**

Add `ContextRecommendationAiClientTest` cases that assert a `POST` to `/api/context-recommend`, `X-Internal-Token`, and this essential JSON shape: `userId: 7`, `weather.condition: "RAIN"`, `weather.temperature: 16.0`, `localTime: "2026-09-27T19:30:00+09:00"`. Add cases for a valid response, `degraded:true`, `albumImageUrl:null`, missing required track values, a non-2xx response, and malformed JSON.

- [ ] **Step 2: Run the new client tests to verify RED**

Run: `./gradlew test --tests '*ContextRecommendationAiClientTest'`

Expected: FAIL because `ContextRecommendationAiClient`, result contract, and recommendation exception do not exist.

- [ ] **Step 3: Implement the AI boundary**

Change `RecommendationAiClient` to carry the documented request context and result state. Add `ContextRecommendationAiClient`, following the timeout, JSON DTO, and token-header pattern in `PhotoRecommendationAiClient`; validate all persistence-required track fields before mapping them. Add `RecommendationAiException` for every upstream or response-contract failure. Change Mock to return a non-degraded `RecommendationResult` and register it only under `ai.mock-enabled=true`; register the actual Client only under `false`.

- [ ] **Step 4: Run the client tests to verify GREEN**

Run: `./gradlew test --tests '*ContextRecommendationAiClientTest' --tests '*MockRecommendationAiClientTest'`

Expected: PASS.

### Task 2: Safe orchestration and HTTP response status

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/service/RecommendationPlaylistCommandService.java:14-28`
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/controller/RecommendationPlaylistController.java:35-44`
- Modify: `src/main/java/com/ktb4/team16/mulo/recommendation/message/RecommendationMessage.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/exception/GlobalExceptionHandler.java:175-180`
- Test: `src/test/java/com/ktb4/team16/mulo/recommendation/service/RecommendationPlaylistCommandServiceTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/recommendation/controller/RecommendationPlaylistControllerTest.java:64-101`

**Interfaces:**
- Consumes: `RecommendationAiClient.RecommendationResult`, `RecommendationPlaylistQueryService`, `RecommendationPlaylistWriter`.
- Produces: a command result containing `Playlist playlist` and `boolean replaced`, which lets the controller select `201` or `200` without changing Writer transaction boundaries.

- [ ] **Step 1: Write failing orchestration tests**

Replace the Mock-specific command test with independent cases proving: a normal non-empty recommendation sends user ID and KST context to the AI Client then calls Writer; `degraded=true` does not call Writer and returns the current playlist; an empty normal response does not call Writer and returns the current playlist; an AI exception does not call Writer.

- [ ] **Step 2: Run the Command Service tests to verify RED**

Run: `./gradlew test --tests '*RecommendationPlaylistCommandServiceTest'`

Expected: FAIL because the existing service treats every returned list as a replacement and has no result state.

- [ ] **Step 3: Implement orchestration and API responses**

Keep `RecommendationPlaylistWriter.replace` unchanged. In Command Service, call AI before Writer, branch on `degraded` or empty tracks, and obtain the existing data from Query Service only on the retain branch. Return an explicit command result. In Controller, use `ResponseEntity` to return `201 Created` when `replaced` and `200 OK` when retained; add a distinct retain message. Map `RecommendationAiException` in the global handler to existing `AI_SERVICE_ERROR`.

- [ ] **Step 4: Write and run controller tests**

Add a `201` test for replacement, a `200` test for retained playlist, a `200` test with `playlist: null` when none exists, and a `502` test for `RecommendationAiException`.

Run: `./gradlew test --tests '*RecommendationPlaylistControllerTest' --tests '*RecommendationPlaylistCommandServiceTest'`

Expected: PASS.

### Task 3: Configuration selection and regression verification

**Files:**
- Test: `src/test/java/com/ktb4/team16/mulo/recommendation/client/RecommendationAiClientConfigurationTest.java`
- Modify: `src/main/resources/application.yaml:67-72` only if a test reveals the documented default does not select the actual Client.

**Interfaces:**
- Consumes: `ai.mock-enabled`, `RecommendationAiClient` implementations.
- Produces: a context test proving one active implementation for each configuration value.

- [ ] **Step 1: Write failing conditional-bean tests**

Create minimal application-context tests: `ai.mock-enabled=true` exposes Mock and not actual Client; `false` exposes actual Client and not Mock.

- [ ] **Step 2: Run configuration tests to verify RED**

Run: `./gradlew test --tests '*RecommendationAiClientConfigurationTest'`

Expected: FAIL because Mock is currently unconditional.

- [ ] **Step 3: Complete minimal configuration changes**

Apply only annotations or configuration necessary for mutually exclusive registration. Preserve the existing `false` default and do not add dependencies.

- [ ] **Step 4: Run targeted and full verification**

Run: `./gradlew checkstyleMain && ./gradlew test`

Expected: PASS with no Checkstyle violations.

### Task 4: Deployment-network integration checklist

**Files:**
- Modify: `docs/ai-api-contract.md` after copying the user-requested contract document into this branch, only if deployment instructions need clarification.

**Interfaces:**
- Consumes: deployment-provided `AI_BASE_URL` and `AI_INTERNAL_TOKEN`.
- Produces: a manual verification record; no source-code behavior.

- [ ] **Step 1: Verify deployment configuration without printing secret values**

Confirm deployment injects `AI_BASE_URL`, `AI_INTERNAL_TOKEN`, and `AI_MOCK_ENABLED=false`; do not place secret values in files, terminal output, commits, or logs.

- [ ] **Step 2: Run deployment-network checks**

From the deployed backend network, call the unauthenticated AI `/health`, then invoke the backend playlist endpoint for normal, degraded, and no-track cases.

- [ ] **Step 3: Record only non-sensitive verification outcomes**

Record HTTP status and replacement/retention result. Do not record tokens or raw upstream error bodies.

## Self-Review

- Spec coverage: Tasks 1-3 cover request mapping, mutually exclusive Beans, validation, `degraded`/empty retention, 502 error conversion, and response status. Task 4 covers the intentionally deferred internal-network integration test.
- Type consistency: Task 1 defines `RecommendationResult`; Task 2 consumes it and returns its own explicit orchestration result; Task 3 only verifies Bean registration.
- Scope: no schema, dependency, CI, monthly-report, memory-search, or playlist-save changes are included.
- Review focus: every listed risk has a named test in Tasks 1-3.
- Commits are intentionally omitted because repository rules require separate approval.
