# MULO Backend Agent Instructions

This file defines mandatory operating rules for AI coding agents working in the `mulo-be` repository.

The agent must follow these instructions before reading, creating, modifying, deleting, or refactoring project code.

---

## 1. Mandatory Reading and Rule Precedence

Before making any change:

1. Read `RULE.md`.
2. Treat `RULE.md` as mandatory team policy.
3. Read this `AGENTS.md` as the AI-specific operating policy.
4. Read any relevant project documentation before changing architecture, database schema, API contracts, security, or testing behavior.
5. Inspect the existing implementation before introducing a new pattern.

Rule precedence for repository work:

1. Explicit instruction from the developer for the current task
2. `RULE.md`
3. `AGENTS.md`
4. Existing project conventions
5. General framework conventions

If instructions conflict or are ambiguous, stop and ask the developer before changing code.

---

## 2. Project Identity

- Repository / Artifact: `mulo-be`
- Service: MULO backend
- Java package root: `com.ktb4.team16.mulo`
- Database name: `mulo`

Do not rename the project, root package, database, or major modules without explicit developer approval.

---

## 3. Technology Baseline

- Java 21
- Spring Boot 4.1.x
- Gradle
- Spring Web MVC
- Spring Data JPA
- MySQL 8.4
- Flyway
- Bean Validation
- Lombok
- Docker Compose for local MySQL development

Do not replace or introduce competing technologies without explicit approval.

Examples requiring approval:

- MyBatis
- QueryDSL
- jOOQ
- PostgreSQL
- Redis
- Kafka
- MapStruct
- additional security/authentication libraries
- new cloud SDKs
- alternative migration tools

Use existing dependencies whenever they are sufficient.

---

## 4. Agent Change Authority

### Allowed without additional approval

The agent may:

- modify implementation code within the existing architecture
- create or modify controllers, services, repositories, DTOs, entities, mappers, and tests
- fix bugs without changing established business rules
- refactor code while preserving observable behavior
- improve naming, readability, null handling, and duplication
- add or update tests for existing behavior
- update comments or documentation related to the requested change

### Explicit developer approval required

The agent must stop and request approval before:

- adding, removing, or upgrading dependencies
- creating, deleting, or modifying a Flyway migration
- changing the database schema
- changing a public API contract
- changing authentication or authorization architecture
- changing package/module architecture
- changing established business rules
- changing persistence strategy
- changing transaction boundaries in a way that affects behavior
- introducing a new infrastructure component
- making broad refactors unrelated to the requested task

When approval is required, explain:

- why the change is necessary
- what files or components would change
- expected impact
- safer alternatives, if any

Do not implement the restricted change until approval is given.

---

## 5. Architecture Rules

Use the existing layered structure unless the repository already defines a more specific convention.

Default request flow:

`Controller -> Service -> Repository -> Database`

### Controller

- Handle HTTP request/response concerns.
- Validate incoming request DTOs.
- Delegate business logic to services.
- Do not contain persistence logic.
- Do not contain significant business logic.

### Service

- Own application and business logic.
- Define transactional boundaries where appropriate.
- Coordinate repositories and external integrations.
- Enforce business rules.

### Repository

- Own persistence access.
- Keep query logic focused on data access.
- Do not place HTTP or presentation concerns here.

### DTO

- Use request/response DTOs for API boundaries.
- Do not expose JPA entities directly as API responses.
- Keep API models separate from persistence models.

### Entity

- Model persistence state and database relationships.
- Keep database-generated columns read-only when appropriate.
- Do not make entities responsible for HTTP concerns.

Follow existing package and naming conventions before creating new structures.

---

## 6. Database, JPA, and Flyway Rules

Flyway owns schema evolution.

Hibernate/JPA maps and validates the schema; it does not own schema creation or migration.

Required policy:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Never change this to `update`, `create`, or `create-drop` unless the developer explicitly instructs otherwise.

### Migration rules

- Never modify a migration that has already been shared or applied.
- Every schema change must use a new Flyway migration.
- Never treat manual changes in MySQL Workbench or a local DB console as the final solution.
- Never silently change schema from application code.
- Do not reuse an already published Flyway version number.
- Check existing migrations before proposing a new version.
- Schema changes require explicit developer approval.

### Entity mapping rules

- Match column names and data types deliberately.
- Preserve nullable/non-nullable semantics.
- Preserve unique constraints and relationship semantics.
- Treat generated columns as database-owned values.
- Use `insertable = false, updatable = false` when the database owns the value.
- Do not weaken database constraints to simplify Java code.

If the Entity and database design disagree, report the mismatch instead of silently changing either side.

---

## 7. API, DTO, and Validation Rules

- Validate external input at the API boundary using Bean Validation where appropriate.
- Use request DTOs and response DTOs.
- Do not return entities directly.
- Do not silently change field names, response shapes, status codes, or endpoint paths.
- Public API contract changes require developer approval.
- Keep validation rules consistent with confirmed service rules and database constraints.
- Do not invent undocumented business rules.

When requirements are missing or contradictory, ask before implementing.

---

## 8. Transaction and Persistence Rules

- Place transaction boundaries primarily in the service layer.
- Use `@Transactional(readOnly = true)` for read-only service operations when appropriate.
- Use write transactions only where state changes occur.
- Avoid unnecessary long-running transactions.
- Do not call external network services inside a transaction unless the existing design explicitly requires it.
- Preserve atomicity for operations that must succeed or fail together.

Do not change transaction behavior merely for convenience.

---

## 9. Error Handling Rules

- Follow the repository's existing exception-handling pattern.
- Do not expose stack traces, SQL errors, credentials, or internal implementation details through API responses.
- Use domain/application exceptions when the project has an established convention.
- Keep error codes/messages consistent with existing API conventions.
- Do not invent new global error formats without approval.

---

## 10. Security and Secrets

Never commit or hard-code:

- passwords
- API keys
- access tokens
- refresh tokens
- cloud credentials
- private keys
- production connection strings

Use environment variables or the project's approved configuration mechanism.

Do not weaken authentication, authorization, password hashing, token handling, or ownership checks without explicit approval.

---

## 11. Dependency Rules

Before proposing a dependency:

1. Check whether the existing JDK, Spring Boot, or current dependencies already solve the problem.
2. Prefer the standard library or existing framework capability.
3. Explain why a new dependency is necessary.
4. Request developer approval.

Do not add a dependency solely to reduce a small amount of code.

---

## 12. Testing and Verification

A task is not complete merely because code was written.

Run the smallest relevant verification first, then broader checks when appropriate.

Default:

```bash
./gradlew test
```

For build-affecting or cross-cutting changes:

```bash
./gradlew clean build
```

For database-related verification, when applicable:

```bash
docker compose up -d
./gradlew test
```

Testing expectations:

- add or update tests when behavior changes
- preserve existing tests
- do not delete failing tests just to make the build green
- investigate failures before changing assertions
- report tests that could not be run and the reason

Never claim a test or build passed unless it was actually run successfully.

---

## 13. Scope Control

Only change what is necessary for the requested task.

Do not:

- perform unrelated refactors
- rename unrelated packages/classes
- reformat the entire repository
- change database naming conventions without need
- restructure modules for personal preference
- rewrite working code without a clear task-related reason

When you discover an unrelated issue, report it separately instead of fixing it automatically.

---

## 14. Prohibited Actions

The agent must not:

- modify already-applied/shared Flyway migrations
- use Hibernate schema auto-update as a migration mechanism
- make manual DB changes the source of truth
- add dependencies without approval
- change API contracts without approval
- change established business rules without approval
- expose entities directly through public APIs
- commit secrets
- bypass validation or authorization for convenience
- suppress errors without understanding the cause
- claim verification that was not performed

---

## 15. Definition of Done

A task is complete only when:

- the requested behavior is implemented
- the change follows `RULE.md` and this file
- the existing architecture is respected
- relevant tests are added or updated when appropriate
- relevant tests pass
- the build passes when applicable
- no unauthorized dependency, schema, API, or business-rule change was introduced
- no unrelated files were changed unnecessarily
- unverified items and remaining risks are clearly reported

---

## 16. Completion Report

When finishing a task, report concisely:

1. What changed
2. Files changed
3. Tests / verification performed
4. Tests not run, if any, and why
5. Database/API/dependency changes, if any
6. Remaining risks or follow-up work

Example:

```text
Changes
- Implemented user profile lookup.
- Added UserProfileResponse DTO.

Files
- UserController.java
- UserService.java
- UserProfileResponse.java

Verification
- ./gradlew test: passed

DB/API/Dependencies
- No schema changes.
- No dependency changes.

Remaining
- None.
```

---

## 17. General Decision Rule

Prefer the smallest correct change that fits the existing design.

When uncertain:

- inspect before editing
- preserve existing behavior
- avoid guessing
- ask before making irreversible or cross-cutting changes
