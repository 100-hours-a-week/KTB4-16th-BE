# User Signup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement unauthenticated, CSRF-exempt `POST /api/users/signup` with policy-defined validation, active-user duplicate handling, BCrypt storage, and structured error responses.

**Architecture:** `user` owns the signup Controller, Service, Repository, Entity, and DTOs. `global/error` owns a single `ErrorCode` Enum plus error response serialization and MVC exception translation. Spring Security continues to guard `/api/**`, but explicitly ignores CSRF only for the signup POST endpoint.

**Tech Stack:** Java 21, Spring Boot 4.1.x, Spring MVC, Spring Security, Spring Data JPA, Bean Validation, MySQL 8.4, Flyway, JUnit 5, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-17-user-signup-design.md`

## Global Constraints

- Use `POST /api/users/signup`; do not change it to `/api/auth/signup`.
- Do not add dependencies or alter Flyway migrations, schema, JWT, Refresh Token, login, logout, or refresh behavior.
- Keep `users.active_email` and `users.active_nickname` database-generated and read-only from JPA.
- Apply BCrypt through the existing `PasswordEncoder` bean; never persist the raw password.
- Signup requires neither authentication nor a CSRF token; no other CSRF policy changes belong in this work.
- Preserve existing Security error JSON as `{ "code", "message" }`; serialize `errors` only when it contains field errors.
- Before any commit or push, obtain the actual signup Issue number and follow `RULE.md` confirmation requirements.
- After successful implementation and verification, copy only the identified learning files to `/Users/bohyeon/Documents/KTB/Team16/mulo-be-study` and add learning-only `TODO: 직접 작성` markers there, never in the production repository.

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/ktb4/team16/mulo/global/error/ErrorCode.java` | Own error code string, HTTP status, and default Korean message. |
| `src/main/java/com/ktb4/team16/mulo/global/error/ErrorResponse.java` | Serialize top-level error and optional field errors. |
| `src/main/java/com/ktb4/team16/mulo/global/error/FieldError.java` | Represent one invalid or duplicate request field. |
| `src/main/java/com/ktb4/team16/mulo/global/error/GlobalExceptionHandler.java` | Convert MVC validation and signup exceptions to API responses. |
| `src/main/java/com/ktb4/team16/mulo/global/security/*` | Replace literal Security error strings with `ErrorCode`. |
| `src/main/java/com/ktb4/team16/mulo/global/config/SecurityConfig.java` | Exempt only signup POST from CSRF validation. |
| `src/main/java/com/ktb4/team16/mulo/user/entity/User.java` | Map the writable `users` columns and timestamps. |
| `src/main/java/com/ktb4/team16/mulo/user/exception/DuplicateUserException.java` | Carry active email and nickname duplicate details. |
| `src/main/java/com/ktb4/team16/mulo/user/repository/UserRepository.java` | Find active user duplicates and save users. |
| `src/main/java/com/ktb4/team16/mulo/user/service/SignupCommand.java` | Carry validated signup input from Controller to Service. |
| `src/main/java/com/ktb4/team16/mulo/user/dto/SignupRequest.java` | Validate nickname, email, and password. |
| `src/main/java/com/ktb4/team16/mulo/user/dto/SignupResponse.java` | Return the documented success message. |
| `src/main/java/com/ktb4/team16/mulo/user/service/UserSignupService.java` | Execute duplicate check, BCrypt encoding, and persistence in one write transaction. |
| `src/main/java/com/ktb4/team16/mulo/user/controller/UserSignupController.java` | Receive the HTTP request and return `201 Created`. |
| `src/test/java/com/ktb4/team16/mulo/...` | Verify error serialization, service behavior, endpoint behavior, and CSRF exemption. |

## Task 1: Establish the shared error model

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/global/error/ErrorCode.java`
- Create: `src/main/java/com/ktb4/team16/mulo/global/error/FieldError.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/error/ErrorResponse.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/security/SecurityErrorWriter.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/security/ApiAuthenticationEntryPoint.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/security/ApiAccessDeniedHandler.java`
- Test: `src/test/java/com/ktb4/team16/mulo/global/error/ErrorResponseTest.java`

**Interfaces:**
- Produces `ErrorCode.status(): HttpStatus`, `ErrorCode.message(): String`.
- Produces `ErrorResponse.of(ErrorCode): ErrorResponse` and `ErrorResponse.of(ErrorCode, List<FieldError>): ErrorResponse`.
- Produces `FieldError.of(String field, ErrorCode code): FieldError`.
- Changes `SecurityErrorWriter.write(HttpServletResponse, ErrorCode)` to derive status and message from the Enum.

- [ ] **Step 1: Write failing serialization tests**

```java
assertThat(writeValueAsString(ErrorResponse.of(ErrorCode.UNAUTHORIZED)))
        .isEqualTo("{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}");
assertThat(writeValueAsString(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE,
        List.of(FieldError.of("email", ErrorCode.INVALID_EMAIL_FORMAT)))))
        .contains("\"errors\":[{")
        .contains("\"field\":\"email\"");
```

- [ ] **Step 2: Run the test to verify failure**

Run: `./gradlew test --tests '*ErrorResponseTest'`

Expected: FAIL because `ErrorCode`, `FieldError`, and the new response factory methods do not exist.

- [ ] **Step 3: Implement the minimal shared error types**

Define every current and signup error code in `ErrorCode`. Put `HttpStatus` and the exact API message in each Enum constant. Use `@JsonInclude(NON_EMPTY)` on `errors` so Security responses retain their two-field JSON form. Update Security writer and handlers to use `ErrorCode.UNAUTHORIZED`, `ErrorCode.FORBIDDEN`, and `ErrorCode.CSRF_TOKEN_INVALID`.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests '*ErrorResponseTest' --tests '*SecurityIntegrationTests' --tests '*ProductionSecurityTests'`

Expected: PASS; existing JSON code assertions remain unchanged.

## Task 2: Create persistent user and signup service

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/user/entity/User.java`
- Create: `src/main/java/com/ktb4/team16/mulo/user/repository/UserRepository.java`
- Create: `src/main/java/com/ktb4/team16/mulo/user/service/SignupCommand.java`
- Create: `src/main/java/com/ktb4/team16/mulo/user/service/UserSignupService.java`
- Create: `src/main/java/com/ktb4/team16/mulo/user/exception/DuplicateUserException.java`
- Test: `src/test/java/com/ktb4/team16/mulo/user/service/UserSignupServiceTest.java`

**Interfaces:**
- `User.signup(String email, String passwordHash, String nickname): User` sets only writable initial state.
- `UserRepository.existsByEmailAndDeletedAtIsNull(String email): boolean`.
- `UserRepository.existsByNicknameAndDeletedAtIsNull(String nickname): boolean`.
- `UserSignupService.signup(SignupCommand command): void` is `@Transactional`.
- `DuplicateUserException.fieldErrors(): List<FieldError>` returns email and/or nickname duplicate details.

- [ ] **Step 1: Write failing service tests**

```java
service.signup(new SignupCommand("뮤로16", "user@example.com", "Test1234!"));
verify(passwordEncoder).encode("Test1234!");
verify(userRepository).save(argThat(user -> passwordEncoder.matches("Test1234!", user.getPasswordHash())));

when(userRepository.existsByEmailAndDeletedAtIsNull("used@example.com")).thenReturn(true);
assertThatThrownBy(() -> service.signup(new SignupCommand("뮤로16", "used@example.com", "Test1234!")))
        .isInstanceOf(DuplicateUserException.class);
verify(userRepository, never()).save(any());
```

- [ ] **Step 2: Run the test to verify failure**

Run: `./gradlew test --tests '*UserSignupServiceTest'`

Expected: FAIL because the user entity, repository, command, service, and duplicate exception do not exist.

- [ ] **Step 3: Implement the JPA mapping and transactional service**

Map `user_id`, `email`, `password_hash`, `nickname`, `music_genre`, `created_at`, `updated_at`, and `deleted_at`. Do not map generated `active_email` or `active_nickname` as writable columns. Check both duplicates before encoding. Build field errors for every detected duplicate, encode once, then save. Catch a database unique-constraint race and convert it to `DuplicateUserException` rather than leaking SQL details.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests '*UserSignupServiceTest'`

Expected: PASS; raw password never reaches the saved entity.

## Task 3: Add request validation, exception translation, and endpoint

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/user/dto/SignupRequest.java`
- Create: `src/main/java/com/ktb4/team16/mulo/user/dto/SignupResponse.java`
- Create: `src/main/java/com/ktb4/team16/mulo/user/controller/UserSignupController.java`
- Create: `src/main/java/com/ktb4/team16/mulo/global/error/GlobalExceptionHandler.java`
- Test: `src/test/java/com/ktb4/team16/mulo/user/controller/UserSignupControllerTest.java`

**Interfaces:**
- `POST /api/users/signup` consumes `SignupRequest` and returns `ResponseEntity<SignupResponse>` with `HttpStatus.CREATED`.
- `SignupRequest.toCommand(): SignupCommand` passes nickname, email, and raw password only to the service boundary.
- `GlobalExceptionHandler` returns `ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, fieldErrors)` for `MethodArgumentNotValidException` and `ErrorResponse.of(ErrorCode.DUPLICATE_RESOURCE, exception.fieldErrors())` for `DuplicateUserException`.

- [ ] **Step 1: Write failing MockMvc tests**

```java
mockMvc.perform(post("/api/users/signup")
        .contentType(APPLICATION_JSON)
        .content("{\"nickname\":\"뮤로16\",\"email\":\"user@example.com\",\"password\":\"Test1234!\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));

mockMvc.perform(post("/api/users/signup").contentType(APPLICATION_JSON)
        .content("{\"nickname\":\"bad name\",\"email\":\"invalid\",\"password\":\"short\"}"))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
    .andExpect(jsonPath("$.errors[*].field").value(hasItems("nickname", "email", "password")));
```

- [ ] **Step 2: Run the test to verify failure**

Run: `./gradlew test --tests '*UserSignupControllerTest'`

Expected: FAIL because endpoint, request DTO, and exception handler do not exist.

- [ ] **Step 3: Implement validation and MVC translation**

Use `@NotBlank`, `@Email`, and `@Pattern` constraints. Convert violations by field and select `*_REQUIRED` before `INVALID_*_FORMAT` for blank values. Use nickname regex `^[A-Za-z0-9가-힣]{2,10}$` and a password regex requiring lowercase, uppercase, digit, special character, and 8~16 total characters. Return only the documented message in `SignupResponse`; do not return entity fields or password hash.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew test --tests '*UserSignupControllerTest'`

Expected: PASS for `201`, `400` field errors, and service-provided `409` duplicate errors.

## Task 4: Exempt only signup from CSRF and verify real persistence flow

**Files:**
- Modify: `src/main/java/com/ktb4/team16/mulo/global/config/SecurityConfig.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/global/security/SecurityIntegrationTests.java`
- Create: `src/test/java/com/ktb4/team16/mulo/user/UserSignupIntegrationTest.java`

**Interfaces:**
- Security ignores CSRF only for `POST /api/users/signup`.
- Other unsafe API requests remain subject to CSRF protection.

- [ ] **Step 1: Write failing Security and integration tests**

```java
mockMvc.perform(post("/api/users/signup").contentType(APPLICATION_JSON)
        .content(validSignupJson))
    .andExpect(status().isCreated());

mockMvc.perform(post("/api/private"))
    .andExpect(status().isForbidden());
```

The persistence test uses a unique email and nickname, then asserts a row exists with a 60-character BCrypt hash that matches the submitted password.

- [ ] **Step 2: Run the test to verify failure**

Run: `./gradlew test --tests '*SecurityIntegrationTests' --tests '*UserSignupIntegrationTest'`

Expected: FAIL because the global CSRF configuration still rejects signup without a token.

- [ ] **Step 3: Implement the narrow Security exception**

Configure CSRF to ignore only `HttpMethod.POST` at `/api/users/signup`. Do not permit any other unsafe endpoint and do not remove the existing CSRF Cookie endpoint.

- [ ] **Step 4: Run full verification**

Run: `docker compose up -d && ./gradlew clean build`

Expected: PASS for compilation, Checkstyle, all Security tests, signup tests, and database-backed integration tests.

## Task 5: Document the manual API flow and prepare the learning copy

**Files:**
- Modify: `docs/security-setup.md`
- Create: `docs/user-signup.md`
- Copy after verification: the implemented user domain and `global/error` core files into `/Users/bohyeon/Documents/KTB/Team16/mulo-be-study`.

**Interfaces:**
- `docs/user-signup.md` gives the exact Postman request and success/error examples.
- Learning copies retain the production signatures but replace method bodies with learning-only `TODO: 직접 작성` markers and ordered comments.

- [ ] **Step 1: Document Postman requests**

Document `POST http://localhost:8080/api/users/signup` without `Authorization`, Cookie, or `X-XSRF-TOKEN` headers. Include one success request, a `400` invalid password response, and a `409` duplicate email response.

- [ ] **Step 2: Prepare learning files after tests pass**

Copy `UserSignupController`, `UserSignupService`, `SignupCommand`, `User`, `UserRepository`, `ErrorCode`, `ErrorResponse`, `FieldError`, and `GlobalExceptionHandler` to `/Users/bohyeon/Documents/KTB/Team16/mulo-be-study`. Mark only method bodies with `TODO: 직접 작성` and add an ordered checklist: Enum → Entity/Repository → Service → Error handler → Controller.

- [ ] **Step 3: Verify copy boundaries**

Run: `rg -n "TODO: 직접 작성" /Users/bohyeon/Documents/KTB/Team16/mulo-be-study/src/main/java/com/ktb4/team16/mulo/{user,global/error}` and compare each copied file with its production counterpart using `diff -u`.

Expected: package names, interfaces, DTO fields, and method signatures match; only learning comments and bodies differ.

- [ ] **Step 4: Prepare delivery details**

Before committing, show the user the actual signup Issue number, staged files, planned commit title, PR title, and target `develop`. Do not commit, push, approve, or merge until the user confirms.

## Plan Self-Review

- Spec coverage: Tasks 1–4 cover ErrorCode unification, public signup, validation, duplicates, BCrypt, CSRF exemption, and tests. Task 5 covers Postman documentation and learning copy.
- Intentional exclusions: JWT, Refresh Token, login, logout, schema migration, and rate limit/email verification/CAPTCHA are absent from tasks.
- Type consistency: `ErrorCode`, `FieldError`, `ErrorResponse`, `SignupCommand`, and `DuplicateUserException` names are defined before later tasks consume them.
