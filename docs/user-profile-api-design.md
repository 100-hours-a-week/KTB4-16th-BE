# 사용자 프로필 API 설계

## 1. 문서 목적

이 문서는 MULO V1 사용자 프로필 API의 개발 순서와 설계 기준을 설명한다. 처음 코드를 확인하는 개발자와 AI도 인증 정보가 프로필 조회로 전달되는 과정, 계층별 책임, 응답 및 오류 계약을 이해할 수 있도록 작성한다.

현재 구현 범위는 `GET /api/users/me`이며, 닉네임 변경은 별도 이슈와 PR에서 이어서 구현한다. 회원탈퇴는 사용자 연관 데이터의 처리 정책과 구현이 준비될 때까지 제외한다.

## 2. 개발 순서

사용자 프로필 기능은 기능별로 별도 이슈, 커밋, PR을 사용한다.

1. 내 정보 조회: `GET /api/users/me`
2. 닉네임 변경: `PATCH /api/users/me/nickname`
3. 회원탈퇴: 현재 범위에서 제외

내 정보 조회를 먼저 구현하는 이유는 JWT로 인증된 사용자 ID가 Controller와 Service를 거쳐 Repository 조회로 연결되는 공통 흐름을 먼저 확립하기 위해서다. 닉네임 변경은 이 인증 및 사용자 조회 흐름을 재사용한다.

## 3. Controller 구성 결정

사용자 도메인의 API 수가 많지 않은 V1 범위를 고려하여 모든 사용자 HTTP 엔드포인트를 `UserController`에 배치한다.

```text
UserController
├── POST  /api/users/signup          기존 회원가입
├── GET   /api/users/me              이번 구현
└── PATCH /api/users/me/nickname     다음 기능에서 추가
```

기존 `UserSignupController`는 `UserController`로 이름을 변경하고 기존 회원가입 메서드를 그대로 유지한다. Controller는 HTTP 요청과 응답의 연결만 담당한다. 비즈니스 로직은 기능별 Service에 분리하여 Controller 통합으로 인해 책임이 섞이지 않도록 한다.

```text
user/
├── controller/
│   └── UserController.java
├── service/
│   ├── UserSignupService.java
│   └── UserProfileService.java
└── dto/
    └── response/
        ├── UserSignupResponse.java
        └── UserProfileResponse.java
```

## 4. 인증과 조회 흐름

```text
Authorization: Bearer <Access Token>
        ↓
JwtAuthenticationFilter
        ↓ Access Token 검증 및 userId 추출
        ↓ 활성 사용자 존재 여부 확인
SecurityContext principal = Long userId
        ↓
UserController.getMyProfile(userId)
        ↓
UserProfileService.getMyProfile(userId)
        ↓
UserRepository.findByUserIdAndDeletedAtIsNull(userId)
        ↓
UserProfileResponse 생성
        ↓
200 OK
```

`SecurityContext`에는 전체 `User` 엔티티가 아니라 `Long userId`만 저장한다. JWT 인증 계층은 요청자를 식별하고, 프로필 Service는 데이터베이스에서 현재 이메일과 닉네임을 조회한다.

이 방식의 근거는 다음과 같다.

- 닉네임과 이메일처럼 변경될 수 있는 값을 Access Token에 의존하지 않는다.
- 인증 계층이 JPA `User` 엔티티 전체를 보관하지 않는다.
- 각 비즈니스 기능이 필요한 최신 데이터만 명시적으로 조회한다.
- Security 계층과 User 도메인의 결합을 줄인다.

## 5. 계층별 책임

### 5.1 UserController

- `GET /api/users/me` 요청을 받는다.
- `@AuthenticationPrincipal Long userId`로 인증 사용자 ID를 받는다.
- JWT 문자열을 직접 파싱하거나 Repository를 직접 호출하지 않는다.
- Service가 반환한 DTO를 `200 OK`로 반환한다.

### 5.2 UserProfileService

- 활성 사용자를 사용자 ID로 조회한다.
- 조회된 엔티티를 API 응답 DTO로 변환한다.
- 조회 전용 메서드에는 `@Transactional(readOnly = true)`를 적용한다.
- 활성 사용자를 찾을 수 없으면 인증 실패로 처리될 예외를 발생시킨다.

### 5.3 UserRepository

다음 조회 메서드를 추가한다.

```java
Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);
```

`deleted_at`이 설정된 사용자는 조회 대상에서 제외한다.

### 5.4 UserProfileResponse

API 명세의 `message`와 `data` 구조를 그대로 표현한다. 이번 기능에서 공통 성공 응답 클래스를 새로 도입하지 않는다. 공통 응답 도입은 기존 성공 응답 전체에 영향을 줄 수 있으므로 별도 리팩터링 범위로 남긴다.

## 6. API 계약

### 6.1 요청

```http
GET /api/users/me
Authorization: Bearer <Access Token>
```

`GET` 요청은 서버 상태를 변경하지 않으므로 CSRF Cookie와 `X-XSRF-TOKEN` 헤더를 요구하지 않는다.

### 6.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "message": "회원 정보 조회 성공",
  "data": {
    "userId": 1,
    "nickname": "뮤로",
    "email": "mulo@example.com"
  }
}
```

성공 메시지는 `UserMessage.PROFILE_RETRIEVED`에서 관리한다.

### 6.3 인증 실패

Access Token이 없거나 만료 또는 위조된 경우 다음 응답을 반환한다.

```http
HTTP/1.1 401 Unauthorized
```

```json
{
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다."
}
```

JWT Filter에서 활성 사용자를 확인한 직후 계정 상태가 변경되어 Service 조회에 실패하는 경우도 사용자 존재 여부를 노출하지 않고 동일한 `401 UNAUTHORIZED`로 처리한다.

### 6.4 서버 오류

예상하지 못한 예외는 기존 `GlobalExceptionHandler`가 `500 INTERNAL_SERVER_ERROR`로 변환한다.

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
}
```

빈 `errors` 배열은 기존 `ErrorResponse`의 `NON_EMPTY` 설정에 따라 응답 JSON에서 제외된다.

## 7. SecurityConfig 영향

새로운 보안 설정은 필요하지 않다. 기존 `requestMatchers("/api/**").authenticated()` 규칙에 따라 `/api/users/me`는 자동으로 인증 요청이 된다.

회원가입, 로그인, 토큰 재발급, 로그아웃에 적용된 공개 접근 규칙은 변경하지 않는다. CI 설정, Flyway 파일, 데이터베이스 스키마도 이번 기능에서 수정하지 않는다.

## 8. 변경 파일

| 파일 | 변경 내용 |
|---|---|
| `user/controller/UserSignupController.java` | 삭제하고 `UserController`로 대체 |
| `user/controller/UserController.java` | 기존 회원가입과 신규 내 정보 조회 엔드포인트 배치 |
| `user/service/UserProfileService.java` | 활성 사용자 조회와 응답 변환 |
| `user/dto/response/UserProfileResponse.java` | `message`, `data` 응답 정의 |
| `user/repository/UserRepository.java` | 활성 사용자 ID 조회 메서드 추가 |
| `user/message/UserMessage.java` | 회원 정보 조회 성공 메시지 추가 |
| `global/exception/UnauthenticatedUserException.java` | 인증 사용자 조회 실패 표현 |
| `global/exception/GlobalExceptionHandler.java` | 인증 사용자 조회 실패를 `401`로 변환 |
| `user/service/UserProfileServiceTest.java` | Service 핵심 동작 검증 |
| `user/controller/UserControllerTest.java` | 기존 회원가입과 프로필 조회의 Web 계층 검증 |
| `user/controller/UserSignupControllerTest.java` | `UserControllerTest`로 대체 |

`User` 엔티티, `SecurityConfig`, DB 스키마 및 Flyway 파일은 변경하지 않는다.

## 9. 최소 테스트와 수동 검증

### 9.1 자동 테스트

개발 속도가 중요한 V1 단계이므로 핵심 회귀만 검증한다.

1. Service가 활성 사용자의 ID, 이메일, 닉네임을 응답으로 변환한다.
2. Service가 활성 사용자를 찾지 못하면 인증 실패 예외를 발생시킨다.
3. 인증된 프로필 조회가 `200 OK`와 명세의 응답 필드를 반환한다.
4. 인증 정보가 없는 프로필 조회가 `401 UNAUTHORIZED`를 반환한다.
5. Controller 통합 후 기존 회원가입 Controller 테스트가 계속 통과한다.

### 9.2 Postman 및 MySQL 검증

1. 로그인 API로 Access Token을 발급받는다.
2. Access Token을 `Authorization: Bearer` 헤더에 넣어 `/api/users/me`를 호출한다.
3. 응답의 `userId`, `email`, `nickname`을 MySQL `users` 행과 비교한다.
4. Authorization 헤더가 없는 요청과 위조 토큰 요청이 `401`인지 확인한다.

## 10. 완료 기준

- API 명세와 동일한 성공 응답을 반환한다.
- 현재 DB에 저장된 최신 이메일과 닉네임을 반환한다.
- 토큰이 없거나 유효하지 않으면 `401`을 반환한다.
- 기존 회원가입 동작에 회귀가 없다.
- 전체 테스트와 Checkstyle이 통과한다.
- 핵심 인증 및 조회 코드에 학습 목적의 주석이 포함된다.
- 구현 결과를 사용자에게 설명하기 전에는 커밋, push, PR을 수행하지 않는다.
