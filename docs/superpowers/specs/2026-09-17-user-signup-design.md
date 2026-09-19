# 회원가입 설계

## 목적

`POST /api/users/signup`으로 활성 사용자를 생성한다. 회원가입은 로그인이나 JWT 발급을 수행하지 않는다.

## 확정된 API 계약

### 요청

```json
{
  "nickname": "뮤로16",
  "email": "user@example.com",
  "password": "Test1234!"
}
```

- `nickname`: 영문, 숫자, 완성형 한글만 허용하며 2~10자다.
- `email`: 필수이며 이메일 형식이어야 한다.
- `password`: 영문 대소문자, 숫자, 특수문자를 모두 포함하는 8~16자다.
- `passwordConfirm`은 클라이언트에서만 검증하며 요청 Body에는 포함하지 않는다.
- 회원가입은 인증과 CSRF 토큰 모두 필요하지 않다.

### 성공 응답

```http
201 Created
```

```json
{
  "message": "회원가입이 완료되었습니다."
}
```

### 실패 응답

입력값 오류는 `400 INVALID_INPUT_VALUE`, 활성 이메일 또는 닉네임 중복은 `409 DUPLICATE_RESOURCE`로 응답한다. 각 필드 오류는 `errors` 배열에 포함한다.

## 패키지와 책임

```text
user
├── controller/UserSignupController
├── service/UserSignupService
├── repository/UserRepository
├── entity/User
├── exception/DuplicateUserException
└── dto
    ├── SignupRequest
    └── SignupResponse

global/error
├── ErrorCode
├── ErrorResponse
├── FieldError
└── GlobalExceptionHandler
```

- Controller는 HTTP 요청 검증과 `201` 응답만 담당한다.
- Service는 중복 검사, BCrypt 해싱, 사용자 생성 및 저장을 담당한다.
- Repository는 활성 이메일·닉네임 중복 조회와 JPA 저장을 담당한다.
- Entity는 `users` 테이블의 실제 저장 상태만 표현한다.

## 핵심 처리 흐름

```text
SignupRequest 형식 검증
→ 활성 email / nickname 중복 검사
→ BCrypt(password)
→ User(email, passwordHash, nickname) 저장
→ 201 Created
```

`music_genre`, `updated_at`, `deleted_at`은 회원가입 시 `NULL`이다. DB의 활성 사용자 UNIQUE 제약조건은 서비스 중복 검사의 최종 안전망이다. 저장 순간의 경합으로 UNIQUE 제약조건이 발생하면 `409 DUPLICATE_RESOURCE`로 변환한다.

## 오류 모델

`ErrorCode` Enum은 HTTP 상태와 기본 메시지를 소유한다. 현재 Security 오류와 회원가입 오류를 한 Enum으로 관리한다.

- 기존 Security: `UNAUTHORIZED`, `FORBIDDEN`, `CSRF_TOKEN_INVALID`
- 회원가입 입력: `INVALID_INPUT_VALUE`, `NICKNAME_REQUIRED`, `INVALID_NICKNAME_FORMAT`, `EMAIL_REQUIRED`, `INVALID_EMAIL_FORMAT`, `PASSWORD_REQUIRED`, `INVALID_PASSWORD_FORMAT`
- 회원가입 중복: `DUPLICATE_RESOURCE`, `EMAIL_DUPLICATED`, `NICKNAME_DUPLICATED`
- 공통: `INTERNAL_SERVER_ERROR`

`ErrorResponse`의 `errors`는 비어 있으면 JSON에서 제외한다. 따라서 기존 Security 응답은 `{ "code", "message" }` 형태를 유지한다. 입력값 오류에서는 빈 값 오류가 형식 오류보다 우선한다.

## 테스트 범위

최소 범위는 다음 네 가지다.

1. 정상 회원가입은 BCrypt 해시를 저장하고 `201`을 반환한다.
2. 이메일 또는 닉네임 중복은 `409`를 반환한다.
3. 입력 형식 오류는 필드별 `errors`와 함께 `400`을 반환한다.
4. CSRF 토큰 없이 회원가입 요청을 보내도 정상 회원가입 흐름을 수행한다.

## 범위 제외

- JWT Access Token 발급 및 검증
- Refresh Token 생성·저장·Cookie 발급
- 로그인, 로그아웃, 재발급
- DB migration 변경

## CSRF 적용 경계

회원가입은 사용자 인증 Cookie나 Refresh Token Cookie를 사용하지 않고, 성공 시에도 인증 Cookie를 발급하지 않는다. 따라서 `POST /api/users/signup`은 CSRF 보호 대상에서 제외한다. `Refresh Token`을 Cookie로 전송하는 재발급·로그아웃 API의 CSRF 적용 여부는 해당 API 설계에서 별도로 결정한다.

## 학습용 복사 계획

구현과 검증이 끝난 뒤, 핵심 파일을 학습용 복사본에 동기화한다. 복사본에는 `TODO: 직접 작성` 표식과 구현 순서를 안내하는 주석을 넣어 사용자가 Controller, Service, ErrorCode/예외 처리, Entity/Repository를 직접 다시 작성할 수 있게 한다. 원본 저장소의 코드에는 학습용 TODO를 넣지 않는다.
