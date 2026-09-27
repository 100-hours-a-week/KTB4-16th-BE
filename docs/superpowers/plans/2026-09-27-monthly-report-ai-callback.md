# Monthly Report AI Callback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Mock monthly-report generation with an asynchronous AI batch request and authenticated, result-bearing AI callback that persists per-user report outcomes.

**Architecture:** Keep monthly aggregation in the backend and save each active user's snapshot before AI dispatch. A non-transactional orchestrator sends the batch request only after preparation commits; a separate callback transaction validates the whole payload and replaces AI-owned recap and scene data idempotently. The existing `MonthlyReport` status enum and tables are reused, with no schema migration.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC `RestClient`, Spring Security, Spring Data JPA, Bean Validation, JUnit 5, Mockito, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-27-monthly-report-ai-callback-design.md`

## Global Constraints

- Keep issue number `#130` and branch `feat/monthly-report-ai-130`.
- Do not add dependencies, modify Flyway migrations, or change the DB schema.
- Read `AI_BASE_URL` and `AI_INTERNAL_TOKEN` exclusively through existing `AiProperties`; never commit or log their values.
- Send `POST {AI_BASE_URL}/api/reports/batch-generate` with `X-Internal-Token` and require `202 Accepted`.
- Save report aggregates before external HTTP; do not hold a DB transaction while calling AI.
- Accept only `POST /internal/ai/report-ready` with a valid `X-Internal-Token`; retain all other security rules.
- Generic malformed callbacks are `400 INVALID_INPUT`; a recap longer than 100 characters is `502 AI_SERVICE_ERROR`; neither changes DB state.
- A valid repeated completed callback replaces photo scene rows instead of duplicating them.

## Review Focus

- Missing or incorrect callback token must receive 401 before controller execution — Task 4 security tests.
- AI returns a non-202 response or malformed 202 body; prepared snapshots must become `FAILED` without exposing AI details — Task 2 client/dispatch tests.
- One invalid callback item must roll back every otherwise-valid item — Task 5 callback transaction tests.
- A 101-character Korean recap must return 502 and leave recap/status/scenes untouched — Task 5 validation tests.
- A repeated completed callback must leave exactly one row per scene tag and must not downgrade a completed report on a later failed result — Task 5 idempotency tests.

## File Structure

- `report/client/MonthlyReportBatchAiClient.java`: sends and validates the AI batch-generation HTTP request.
- `report/client/MonthlyReportAiException.java`: safe exception for AI transport, batch-contract, and overlength-recap failures.
- `report/service/MonthlyReportPreparationService.java`: persists backend-owned aggregate snapshots as `PENDING`, returns prepared user IDs, and performs post-dispatch status transitions in separate transactions.
- `report/service/MonthlyReportGenerationService.java`: non-transactional scheduler facade that dispatches a prepared batch and selects `PROCESSING` or `FAILED` transitions.
- `report/service/MonthlyReportAiCallbackService.java`: atomically validates and applies the AI callback result set.
- `report/controller/MonthlyReportAiCallbackController.java`: exposes the private callback endpoint only.
- `report/dto/request/MonthlyReportAiCallbackRequest.java`: callback JSON records and Bean Validation declarations.
- `global/security/AiInternalTokenFilter.java`: guards exactly the internal callback route with `X-Internal-Token`.
- `SecurityConfig.java`: narrowly permits and excludes only the callback route from JWT/CSRF processing.
- `MonthlyReport.java` and report repositories: add explicit status transition and lookup/delete operations while preserving the schema.

### Task 1: Model the asynchronous report lifecycle

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/report/entity/MonthlyReport.java:20-43`
- Modify: `src/main/java/com/ktb4/team16/mulo/report/repository/MonthlyReportRepository.java:7-13`
- Modify: `src/main/java/com/ktb4/team16/mulo/report/repository/MonthlyPhotoSceneStatRepository.java:3-6`
- Test: `src/test/java/com/ktb4/team16/mulo/report/entity/MonthlyReportTest.java`

**Interfaces:**
- Produces: `MonthlyReport.prepare(...)`, `markProcessing()`, `markFailedUnlessCompleted()`, `complete(String recapText)`.
- Produces: repository lookup by `(userId, reportYear, reportMonth)`, period snapshot lookup for full callback validation, and deletion of scene rows by `monthlyReportId`.
- Consumes: existing schema constraints from the approved spec.

- [ ] **Step 1: Write failing entity/repository tests**

Test `prepare` starts with `PENDING` and no recap, `markProcessing` reaches `PROCESSING`, `complete` stores recap and reaches `COMPLETED`, and late `markFailedUnlessCompleted` preserves `COMPLETED`.

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests '*MonthlyReportTest'`

Expected: FAIL because the lifecycle methods and repository operations do not exist.

- [ ] **Step 3: Add explicit lifecycle methods and repository signatures**

Implement the exact methods above. Add only lookup/delete methods needed by preparation and callback services; do not alter table mapping, enum values, or migration files.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew test --tests '*MonthlyReportTest'`

Expected: PASS.

- [ ] **Step 5: Commit the lifecycle unit**

```bash
git add src/main/java/com/ktb4/team16/mulo/report/entity/MonthlyReport.java src/main/java/com/ktb4/team16/mulo/report/repository/MonthlyReportRepository.java src/main/java/com/ktb4/team16/mulo/report/repository/MonthlyPhotoSceneStatRepository.java src/test/java/com/ktb4/team16/mulo/report/entity/MonthlyReportTest.java
git commit -m "feat: 월간 리포트 AI 상태 전이 추가 #130"
```

### Task 2: Implement the AI batch-dispatch client

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/report/client/MonthlyReportBatchAiClient.java`
- Create: `src/main/java/com/ktb4/team16/mulo/report/client/MonthlyReportAiException.java`
- Delete: `src/main/java/com/ktb4/team16/mulo/report/client/MonthlyReportAiClient.java`
- Delete: `src/main/java/com/ktb4/team16/mulo/report/client/MockMonthlyReportAiClient.java`
- Test: `src/test/java/com/ktb4/team16/mulo/report/client/MonthlyReportBatchAiClientTest.java`

**Interfaces:**
- Consumes: `AiProperties` from `recommendation.config` and `YearMonth`, `List<Long>`.
- Produces: `BatchAccepted(String jobId, int targetCount)` from `requestBatch(YearMonth targetMonth, List<Long> userIds)`.
- Produces: `MonthlyReportAiException` for transport failure, non-202 response, absent/blank job ID, or invalid target count.

- [ ] **Step 1: Write failing HTTP client tests**

Use `MockRestServiceServer` to assert `POST http://ai.test/api/reports/batch-generate`, the `X-Internal-Token` header, and this JSON shape:

```json
{ "year": 2026, "month": 9, "userIds": [7, 12] }
```

Assert a valid `202` JSON produces `BatchAccepted`; assert 400/401/501/503, malformed JSON, and a 202 response without `jobId` throw `MonthlyReportAiException` without exposing a response body.

- [ ] **Step 2: Run the client test to verify it fails**

Run: `./gradlew test --tests '*MonthlyReportBatchAiClientTest'`

Expected: FAIL because the client and exception classes do not exist.

- [ ] **Step 3: Implement `MonthlyReportBatchAiClient`**

Follow the existing `PhotoRecommendationAiClient` `RestClient` timeout pattern. Append `/api/reports/batch-generate` to `AiProperties.baseUrl()`, send the required header, deserialize only `status`, `jobId`, and `targetCount`, and accept only `status == QUEUED` with a positive target count.

- [ ] **Step 4: Run the client test to verify it passes**

Run: `./gradlew test --tests '*MonthlyReportBatchAiClientTest'`

Expected: PASS.

- [ ] **Step 5: Commit the AI batch client**

```bash
git add src/main/java/com/ktb4/team16/mulo/report/client src/test/java/com/ktb4/team16/mulo/report/client/MonthlyReportBatchAiClientTest.java
git commit -m "feat: 월간 리포트 AI 배치 요청 추가 #130"
```

### Task 3: Prepare snapshots, dispatch after commit, and transition status

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportPreparationService.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportGenerationService.java:21-51`
- Modify: `src/main/java/com/ktb4/team16/mulo/report/scheduler/MonthlyReportScheduler.java:10-17`
- Modify: `src/main/java/com/ktb4/team16/mulo/report/controller/LocalMonthlyReportGenerationController.java:21-34`
- Modify: `src/test/java/com/ktb4/team16/mulo/report/service/MonthlyReportGenerationServiceTest.java:31-68`
- Create: `src/test/java/com/ktb4/team16/mulo/report/service/MonthlyReportPreparationServiceTest.java`

**Interfaces:**
- Produces: `PreparedBatch(YearMonth targetMonth, List<Long> userIds, int createdCount, int skippedCount)` from `prepare(YearMonth)`, `markProcessing(PreparedBatch)`, and `markFailed(PreparedBatch)`.
- Consumes: `MonthlyReportBatchAiClient.requestBatch(YearMonth, List<Long>)`.
- Produces: existing `GenerationResult`; existing scheduler and local controller signatures remain stable.

- [ ] **Step 1: Write failing preparation and dispatch tests**

Cover: active users create aggregate/mood snapshots with `PENDING`; existing user-period snapshots are skipped; zero prepared users makes no HTTP call; valid `202` changes all prepared reports to `PROCESSING`; client failure changes them to `FAILED` while retaining backend aggregate rows.

- [ ] **Step 2: Run service tests to verify they fail**

Run: `./gradlew test --tests '*MonthlyReportPreparationServiceTest' --tests '*MonthlyReportGenerationServiceTest'`

Expected: FAIL because the existing service calls Mock AI inside its transaction.

- [ ] **Step 3: Implement preparation and orchestration boundaries**

Move current record/place/artist/mood aggregation into `MonthlyReportPreparationService.prepare(YearMonth)` with `@Transactional`. Add `markProcessing(PreparedBatch)` and `markFailed(PreparedBatch)` as separate `@Transactional` methods. Make `MonthlyReportGenerationService.generate(YearMonth)` non-transactional: call preparation, skip AI for an empty batch, call the client, then invoke the matching preparation-service status method. Preserve KST scheduler timing and local endpoint response counts.

- [ ] **Step 4: Run service tests to verify they pass**

Run: `./gradlew test --tests '*MonthlyReportPreparationServiceTest' --tests '*MonthlyReportGenerationServiceTest'`

Expected: PASS.

- [ ] **Step 5: Commit snapshot preparation and dispatch orchestration**

```bash
git add src/main/java/com/ktb4/team16/mulo/report/service src/main/java/com/ktb4/team16/mulo/report/scheduler/MonthlyReportScheduler.java src/main/java/com/ktb4/team16/mulo/report/controller/LocalMonthlyReportGenerationController.java src/test/java/com/ktb4/team16/mulo/report/service
git commit -m "feat: 월간 리포트 비동기 생성 흐름 적용 #130"
```

### Task 4: Secure and expose the internal AI callback route

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/global/security/AiInternalTokenFilter.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/config/SecurityConfig.java:38-74`
- Create: `src/main/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackController.java`
- Create: `src/main/java/com/ktb4/team16/mulo/report/dto/request/MonthlyReportAiCallbackRequest.java`
- Test: `src/test/java/com/ktb4/team16/mulo/global/security/AiInternalTokenFilterTest.java`
- Test: `src/test/java/com/ktb4/team16/mulo/global/security/SecurityIntegrationTests.java`
- Test: `src/test/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackControllerTest.java`

**Interfaces:**
- Consumes: `AiProperties.internalToken()` and `SecurityErrorWriter`.
- Produces: `POST /internal/ai/report-ready`; controller delegates `MonthlyReportAiCallbackService.apply(MonthlyReportAiCallbackRequest)`.
- Produces: `401 UNAUTHORIZED` for a missing or mismatched header before MVC handling.

- [ ] **Step 1: Write failing filter, security, and controller tests**

Assert the exact callback path rejects missing/wrong `X-Internal-Token` with 401, allows a correct token through without JWT or CSRF token, and does not relax access to an unrelated `/internal/**` path. Assert the controller sends a valid request to its service and returns 204.

- [ ] **Step 2: Run security tests to verify they fail**

Run: `./gradlew test --tests '*AiInternalTokenFilterTest' --tests '*SecurityIntegrationTests' --tests '*MonthlyReportAiCallbackControllerTest'`

Expected: FAIL because the route, filter, DTO, and narrow security exception do not exist.

- [ ] **Step 3: Implement exact-route token security and request DTOs**

Implement a `OncePerRequestFilter` whose `shouldNotFilter` is false only for `POST /internal/ai/report-ready`; compare the configured and supplied values without logging either. Reuse `SecurityErrorWriter` for 401. In `SecurityConfig`, permit and CSRF-ignore exactly that POST route, place the filter before JWT authentication, and retain `anyRequest().denyAll()`. Define nested request records with Bean Validation for structural constraints; reserve cross-result and recap-length checks for Task 5.

- [ ] **Step 4: Run security tests to verify they pass**

Run: `./gradlew test --tests '*AiInternalTokenFilterTest' --tests '*SecurityIntegrationTests' --tests '*MonthlyReportAiCallbackControllerTest'`

Expected: PASS.

- [ ] **Step 5: Commit callback route security**

```bash
git add src/main/java/com/ktb4/team16/mulo/global/security/AiInternalTokenFilter.java src/main/java/com/ktb4/team16/mulo/global/config/SecurityConfig.java src/main/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackController.java src/main/java/com/ktb4/team16/mulo/report/dto/request/MonthlyReportAiCallbackRequest.java src/test/java/com/ktb4/team16/mulo/global/security src/test/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackControllerTest.java
git commit -m "feat: AI 월간 리포트 콜백 인증 추가 #130"
```

### Task 5: Apply callback results atomically and idempotently

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportAiCallbackService.java`
- Create: `src/main/java/com/ktb4/team16/mulo/report/exception/InvalidMonthlyReportAiCallbackException.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/exception/GlobalExceptionHandler.java:133-180`
- Test: `src/test/java/com/ktb4/team16/mulo/report/service/MonthlyReportAiCallbackServiceTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackControllerTest.java`

**Interfaces:**
- Consumes: validated `MonthlyReportAiCallbackRequest` and report/scene repositories.
- Produces: `void apply(MonthlyReportAiCallbackRequest callback)` under one transaction.
- Produces: `InvalidMonthlyReportAiCallbackException` mapped to 400 and `MonthlyReportAiException` mapped to existing `AI_SERVICE_ERROR` 502.

- [ ] **Step 1: Write failing callback-service tests**

Test a mixed callback: one completed report gains recap/scenes and `COMPLETED`; one failed report becomes `FAILED`. Test a duplicate completed result deletes prior scenes before saving replacements. Test late failed data cannot downgrade an already completed report. Test an unknown snapshot, duplicate user ID, invalid scene bounds, or missing required result field causes no repository write. Test a 101-character Korean recap throws `MonthlyReportAiException` and causes no repository write.

- [ ] **Step 2: Run callback tests to verify they fail**

Run: `./gradlew test --tests '*MonthlyReportAiCallbackServiceTest' --tests '*MonthlyReportAiCallbackControllerTest'`

Expected: FAIL because callback application and exception mapping do not exist.

- [ ] **Step 3: Implement full-payload validation then transactional application**

Validate every result and matching `(userId, year, month)` snapshot before mutation. Reject the complete request with `InvalidMonthlyReportAiCallbackException` for generic invalid data; throw `MonthlyReportAiException` only for recap length over 100. After validation, delete scene rows for each completed report, save replacement rows, complete the report, and mark failed only if the current report is not completed. Map the two exceptions in `GlobalExceptionHandler` without exposing AI details.

- [ ] **Step 4: Run callback tests to verify they pass**

Run: `./gradlew test --tests '*MonthlyReportAiCallbackServiceTest' --tests '*MonthlyReportAiCallbackControllerTest'`

Expected: PASS.

- [ ] **Step 5: Commit callback persistence**

```bash
git add src/main/java/com/ktb4/team16/mulo/report/service/MonthlyReportAiCallbackService.java src/main/java/com/ktb4/team16/mulo/report/exception/InvalidMonthlyReportAiCallbackException.java src/main/java/com/ktb4/team16/mulo/global/exception/GlobalExceptionHandler.java src/test/java/com/ktb4/team16/mulo/report/service/MonthlyReportAiCallbackServiceTest.java src/test/java/com/ktb4/team16/mulo/report/controller/MonthlyReportAiCallbackControllerTest.java
git commit -m "feat: AI 월간 리포트 결과 저장 추가 #130"
```

### Task 6: Run regression verification and document deployment inputs

**Files:**
- Create: `docs/monthly-report-ai-callback.md`
- Test: relevant report client, service, controller, and security test classes.

**Interfaces:**
- Produces: an implementation-facing callback contract document with no secret values.
- Consumes: the approved design document and actual code/test results.

- [ ] **Step 1: Update the implementation-facing contract documentation**

Document the exact batch request, callback request, state transitions, 204/400/401/502 outcomes, and deployment `AI_BASE_URL` values (`http://ai:8000` in the shared Docker network; `https://mulostudio.com/ai` for local gateway testing). Do not copy any token.

- [ ] **Step 2: Run focused quality checks**

Run:

```bash
./gradlew checkstyleMain
./gradlew test --tests '*MonthlyReport*' --tests '*AiInternalTokenFilterTest' --tests '*SecurityIntegrationTests'
git diff --check
```

Expected: all commands PASS.

- [ ] **Step 3: Run full regression with local DB environment**

Run:

```bash
set -a
source .env
set +a
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit documentation and verification-ready changes**

```bash
git add docs/monthly-report-ai-callback.md
git diff --cached --check
git commit -m "docs: 월간 리포트 AI 콜백 계약 정리 #130"
```

## Plan Self-Review

- Spec coverage: Tasks 1-3 cover snapshot preparation, batch dispatch, and status transitions; Task 4 covers the internal endpoint and authentication; Task 5 covers callback validation, atomic persistence, and idempotency; Task 6 covers documentation and regression verification.
- Type consistency: the batch client returns `BatchAccepted`; preparation returns `PreparedBatch`; the existing public `GenerationResult` remains the scheduler/local-controller output; callback input is `MonthlyReportAiCallbackRequest`.
- Failure coverage: all five Review Focus conditions are assigned to Tasks 2, 4, and 5 with explicit tests.
- Scope: no polling API, job table, dependency, schema, or Flyway work is included because the approved callback contains full results.
