# 오류 코드와 API 메시지 관리 규칙

## 1. 문서 목적

이 문서는 MULO 백엔드에서 오류 코드·오류 메시지·성공 메시지를 어디에 두고 어떻게 사용하는지 설명한다.
처음 프로젝트를 확인하는 개발자와 AI 에이전트가 기존 규칙을 다시 추론하지 않고 같은 방식으로 기능을 추가하는 것이 목적이다.

이 규칙은 다음 두 원칙을 함께 적용한다.

1. `user`, `auth`, `place`처럼 기능을 기준으로 최상위 도메인을 나눈다.
2. 모든 도메인이 공유하는 HTTP 오류 응답 규칙만 `global`에 둔다.

Spring Modulith 공식 문서는 애플리케이션 모듈을 기능 단위로 구성하고, 각 모듈이 외부에 제공하는 API와 내부 구현을 구분하는 방식을 설명한다. MULO는 Spring Modulith 의존성을 사용하지 않지만, 도메인별 최상위 패키지를 기능 경계로 사용한다는 설계 방향은 이 개념을 참고했다.

- [Spring Modulith - Application Modules](https://docs.spring.io/spring-modulith/reference/fundamentals.html#application-modules)

> 주의: 공식 문서가 MULO의 정확한 폴더 구성을 강제하는 것은 아니다. 현재 구조는 두 명이 도메인을 나누어 개발한다는 팀 상황과 공통 응답 계약을 함께 고려해 결정한 프로젝트 규칙이다.

## 2. 현재 패키지 구조

```text
com.ktb4.team16.mulo
├── global
│   ├── error
│   │   ├── ErrorCode.java
│   │   ├── ErrorResponse.java
│   │   └── FieldError.java
│   └── exception
│       └── GlobalExceptionHandler.java
├── user
│   ├── exception
│   │   └── DuplicateUserException.java
│   └── message
│       └── UserMessage.java
└── auth
    ├── exception
    │   ├── InvalidCredentialsException.java
    │   └── InvalidRefreshTokenException.java
    └── message
        └── AuthMessage.java
```

각 위치의 책임은 다음과 같다.

| 위치 | 책임 | 배치 예시 |
| --- | --- | --- |
| `global/error` | 모든 API가 공유하는 오류 응답 형식과 오류 식별자 | `ErrorCode`, `ErrorResponse`, `FieldError` |
| `global/exception` | 발생한 예외를 HTTP 오류 응답으로 변환하는 공통 규칙 | `GlobalExceptionHandler` |
| `{domain}/exception` | 해당 도메인의 업무 상황을 나타내는 예외 | `DuplicateUserException` |
| `{domain}/message` | 해당 도메인의 성공 응답 문구 | `UserMessage`, `AuthMessage` |

## 3. 오류와 성공 메시지를 분리한 이유

### 3.1 오류 코드는 `global/error`에서 통합 관리한다

클라이언트가 받는 오류 응답은 어느 도메인에서 발생해도 같은 형태를 사용한다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다."
}
```

필드 오류가 있으면 같은 응답에 `errors`가 추가된다.

```json
{
  "code": "INVALID_INPUT_VALUE",
  "message": "입력값을 확인해주세요.",
  "errors": [
    {
      "field": "email",
      "code": "EMAIL_REQUIRED",
      "message": "이메일을 입력해주세요."
    }
  ]
}
```

도메인마다 오류 응답 클래스를 만들면 JSON 구조와 필드 이름이 달라질 수 있다. 이를 방지하기 위해 다음 세 클래스를 공통으로 둔다.

- `ErrorCode`: 프로그램이 판별할 코드, HTTP 상태, 사용자 메시지를 하나의 항목으로 묶는다.
- `ErrorResponse`: 최상위 오류 JSON 계약을 정의한다.
- `FieldError`: 입력 필드별 세부 오류를 정의한다.

`ErrorCode`를 하나의 enum으로 관리하는 것은 현재 V1 규모에서 오류 검색과 API 명세 대조를 단순하게 하기 위한 팀 결정이다. 오류 수가 크게 증가해 한 파일의 탐색성이 낮아지면 도메인별 코드 enum 분리를 다시 검토할 수 있지만, 외부 `ErrorResponse` 형식은 유지해야 한다.

### 3.2 성공 메시지는 도메인 내부에서 관리한다

성공 메시지는 공통 오류 계약과 달리 특정 기능의 의미를 설명한다.

- `UserMessage.SIGNUP_COMPLETED`: 회원가입 성공
- `AuthMessage.LOGIN_COMPLETED`: 로그인 성공
- `AuthMessage.LOGOUT_COMPLETED`: 로그아웃 성공

성공 메시지를 `global`에 모두 모으면 도메인이 늘어날수록 서로 관련 없는 문구가 한 파일에 섞인다. 따라서 성공 메시지는 이를 사용하는 Controller와 같은 도메인에 둔다. 이 방식은 기능 단위 패키지의 응집도를 유지하고, 담당 개발자가 자신의 도메인 안에서 관련 코드를 찾기 쉽게 한다.

### 3.3 커스텀 예외는 발생 도메인에 둔다

`InvalidCredentialsException`은 인증 규칙을, `DuplicateUserException`은 사용자 가입 중복을 의미한다. 이 예외들은 HTTP 응답 자체가 아니라 업무 상황을 나타내므로 해당 도메인에 둔다.

Service는 HTTP 상태를 직접 선택하지 않고 도메인 예외를 발생시킨다. `GlobalExceptionHandler`가 예외를 받아 `ErrorCode`와 HTTP 상태로 변환한다. 이 분리는 Service가 HTTP 표현 계층에 직접 의존하지 않도록 한다.

### 3.4 예외 변환은 `global/exception`에서 담당한다

Spring의 `@RestControllerAdvice`는 `@ControllerAdvice`와 `@ResponseBody`를 결합해 여러 Controller에 공통으로 적용되는 예외 처리 결과를 응답 본문으로 작성한다. `@ExceptionHandler`는 처리할 예외 유형과 변환 메서드를 연결한다.

- [Spring Framework - `@RestControllerAdvice`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/RestControllerAdvice.html)
- [Spring Framework - Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)

MULO는 이 기능을 `GlobalExceptionHandler` 한 곳에서 사용해 다음을 보장한다.

- 동일한 예외는 어느 Controller에서 발생해도 같은 상태와 본문을 반환한다.
- Service와 Controller에서 오류 JSON을 반복 생성하지 않는다.
- 예외 메시지, SQL 오류, 스택 트레이스 같은 내부 정보를 클라이언트에 노출하지 않는다.

## 4. 요청에서 응답까지의 흐름

### 4.1 입력값 검증 오류

```text
HTTP 요청
→ Controller의 @Valid @RequestBody
→ 요청 DTO의 @NotBlank, @Email, @Pattern 검사
→ MethodArgumentNotValidException
→ GlobalExceptionHandler.handleInvalidRequest()
→ INVALID_INPUT_VALUE + FieldError 목록
→ HTTP 400
```

Spring MVC 공식 문서는 `@Valid @RequestBody`에 Bean Validation을 적용하며, 검증 실패 시 기본적으로 `MethodArgumentNotValidException`과 400 응답이 발생한다고 설명한다. MULO는 이 예외를 직접 변환해 API 명세에 정의한 필드 오류 배열을 만든다.

- [Spring Framework - Request Body Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)
- [Spring Framework - MVC Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html)

DTO 제약 조건의 `message`에는 사용자 문구가 아니라 `ErrorCode` enum 이름을 기록한다.

```java
@NotBlank(message = "EMAIL_REQUIRED")
@Email(message = "INVALID_EMAIL_FORMAT")
String email
```

`GlobalExceptionHandler`가 이 이름을 `ErrorCode.valueOf(...)`로 변환하고, 최종 사용자 문구는 `ErrorCode.message()`에서 가져온다. 따라서 같은 오류의 문구를 DTO마다 반복하지 않는다.

### 4.2 도메인 오류

```text
Controller
→ Service
→ 도메인 규칙 위반 감지
→ 도메인 커스텀 예외 발생
→ GlobalExceptionHandler의 @ExceptionHandler
→ ErrorCode와 ErrorResponse 생성
→ 해당 HTTP 상태로 응답
```

예를 들어 로그인 실패는 이메일 존재 여부와 비밀번호 오류를 외부에서 구분하지 않고 모두 `InvalidCredentialsException`으로 처리한다. 예외 처리기는 이를 `INVALID_CREDENTIALS`와 401로 변환한다.

### 4.3 예상하지 못한 오류

처리기가 명시적으로 알고 있는 예외가 아니면 `handleUnexpectedException()`이 `INTERNAL_SERVER_ERROR`와 500을 반환한다. 내부 예외의 원문은 응답에 포함하지 않는다.

운영 환경에서는 예외를 응답에 노출하는 대신 서버 로그와 모니터링에서 원인을 추적해야 한다. 현재 구현은 공통 500 응답 계약까지만 담당한다.

### 4.4 성공 응답

```text
Controller
→ Service 정상 완료
→ 같은 도메인의 Message enum 선택
→ Response DTO 생성
→ 2xx 응답
```

성공 메시지는 예외 처리기를 거치지 않는다. Controller가 자신의 도메인 Message enum을 사용해 Response DTO를 만든다.

## 5. 현재 ErrorCode 분류

| 분류 | 코드 | HTTP 상태 | 사용 목적 |
| --- | --- | ---: | --- |
| 인증·인가 | `UNAUTHORIZED` | 401 | 인증이 필요한 API에 유효한 Access Token이 없음 |
| 인증·인가 | `FORBIDDEN` | 403 | 인증됐지만 접근 권한이 없음 |
| 보안 | `CSRF_TOKEN_INVALID` | 403 | CSRF Cookie와 헤더 검증 실패 |
| 인증 | `INVALID_CREDENTIALS` | 401 | 로그인 이메일 또는 비밀번호 불일치 |
| 인증 | `INVALID_REFRESH_TOKEN` | 401 | Refresh Token 누락·위조·만료·폐기 등 재발급 실패 |
| 입력 | `INVALID_INPUT_VALUE` | 400 | 하나 이상의 요청 필드 검증 실패를 감싸는 상위 코드 |
| 입력 | `*_REQUIRED` | 400 | 필수값 누락 |
| 입력 | `INVALID_*_FORMAT` | 400 | 형식·길이·조합 규칙 위반 |
| 중복 | `DUPLICATE_RESOURCE` | 409 | 하나 이상의 중복 필드 오류를 감싸는 상위 코드 |
| 중복 | `EMAIL_DUPLICATED` | 409 | 활성 사용자의 이메일 중복 |
| 중복 | `NICKNAME_DUPLICATED` | 409 | 활성 사용자의 닉네임 중복 |
| 서버 | `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 내부 처리 실패 |

상위 코드와 필드 코드를 구분한다.

- 최상위 `code`: 요청 전체의 실패 종류
- `errors[].code`: 특정 필드의 정확한 실패 원인

예를 들어 이메일과 닉네임이 동시에 중복이면 최상위 코드는 `DUPLICATE_RESOURCE` 하나이고, `errors`에는 두 필드 코드가 들어간다.

## 6. 새 도메인 기능을 추가하는 방법

### 6.1 성공 메시지 추가

1. 해당 도메인의 `message` 패키지를 확인한다.
2. 기존 Message enum이 있으면 새 상수를 추가한다.
3. Controller는 문자열을 직접 작성하지 않고 enum의 `message()`를 사용한다.
4. API 명세의 성공 문구와 테스트의 기대값을 함께 확인한다.

```java
public enum PlaceMessage {
    PLACE_CREATED("장소가 생성되었습니다.");
}
```

성공 응답에 메시지가 없는 API라면 억지로 Message enum을 추가하지 않는다. 예를 들어 Access Token 재발급 응답은 명세대로 `accessToken`만 반환한다.

### 6.2 오류 코드 추가

먼저 기존 `ErrorCode`가 같은 의미를 표현하는지 확인한다.

- 같은 의미가 있으면 재사용한다.
- 클라이언트가 별도로 구분해야 하는 새로운 실패 상황이면 새 코드를 추가한다.
- HTTP 상태만 다르거나 메시지만 조금 다르다는 이유로 중복 코드를 만들지 않는다.
- 코드 이름, HTTP 상태, 메시지는 API 명세와 함께 확정한다.

### 6.3 커스텀 예외 추가

1. 예외가 발생하는 도메인의 `exception` 패키지에 작성한다.
2. 예외에는 필요한 업무 정보만 담는다.
3. `GlobalExceptionHandler`에 예외와 `ErrorCode` 변환을 추가한다.
4. Service 테스트에서 예외 발생 조건을 확인한다.
5. Controller 또는 예외 처리 테스트에서 최종 상태·코드·메시지를 확인한다.

### 6.4 DTO 검증 코드 추가

DTO 제약 조건의 `message` 값은 반드시 실제 `ErrorCode` 이름과 일치해야 한다. 일치하지 않으면 `ErrorCode.valueOf()`에서 오류가 발생해 의도한 400 대신 500이 될 수 있다.

새 필드 검증을 추가할 때는 다음을 함께 확인한다.

- 필수값 누락
- 형식과 길이의 경계값
- 동시에 여러 필드가 실패할 때의 `errors` 개수
- API 명세의 코드·메시지와 실제 JSON

## 7. 테스트 기준

API별로 명세에 정의된 성공·실패 응답 유형을 각각 테스트한다.

- HTTP 상태
- 최상위 `code`
- 사용자 `message`
- 필드 오류의 `field`, `code`, `message`
- 여러 필드가 실패할 때 `errors` 배열의 정확한 개수
- 500 응답에 내부 예외 정보가 노출되지 않는지

Service 테스트는 HTTP JSON 대신 업무 조건과 발생 예외를 확인한다. Controller 테스트는 Service 결과 또는 예외가 최종 API 계약으로 올바르게 변환되는지 확인한다.

## 8. 금지하는 방식

### Controller에서 메시지 문자열 직접 작성

```java
// 사용하지 않는다.
return new UserSignupResponse("회원가입이 완료되었습니다.");
```

문구가 여러 곳에 복제되면 변경 시 일부만 수정될 수 있다. 해당 도메인의 Message enum을 사용한다.

### Service에서 HTTP 응답 생성

```java
// 사용하지 않는다.
return ResponseEntity.status(HttpStatus.CONFLICT).body(...);
```

Service는 도메인 예외를 발생시키고 HTTP 변환은 `GlobalExceptionHandler`에 맡긴다.

### 예외 원문을 사용자에게 반환

SQL, JWT 파싱, 스택 트레이스, 환경변수 등 내부 정보는 `message`에 넣지 않는다.

### 같은 의미의 코드 중복 생성

도메인이 다르더라도 클라이언트가 동일하게 처리해야 하는 오류라면 공통 코드를 재사용한다. 반대로 같은 HTTP 상태라도 클라이언트의 후속 동작이 다르면 별도 코드를 사용한다.

## 9. 개발자와 AI 에이전트 체크리스트

- [ ] 변경 전 API 명세의 성공·실패 행을 확인했는가?
- [ ] 성공 메시지를 해당 도메인의 `message`에 두었는가?
- [ ] 기존 `ErrorCode`를 재사용할 수 있는지 확인했는가?
- [ ] 도메인 커스텀 예외를 해당 도메인의 `exception`에 두었는가?
- [ ] 예외에서 HTTP 응답으로의 변환을 `GlobalExceptionHandler`에 두었는가?
- [ ] DTO 제약 조건의 message가 실제 ErrorCode 이름과 일치하는가?
- [ ] 상태·코드·메시지·필드 오류 개수를 테스트했는가?
- [ ] 오류 응답에 내부 구현 정보나 Secret이 포함되지 않는가?
- [ ] 새 규칙을 추가했다면 이 문서와 API 명세를 함께 수정했는가?

## 10. 현재 구현 파일

- `src/main/java/com/ktb4/team16/mulo/global/error/ErrorCode.java`
- `src/main/java/com/ktb4/team16/mulo/global/error/ErrorResponse.java`
- `src/main/java/com/ktb4/team16/mulo/global/error/FieldError.java`
- `src/main/java/com/ktb4/team16/mulo/global/exception/GlobalExceptionHandler.java`
- `src/main/java/com/ktb4/team16/mulo/user/message/UserMessage.java`
- `src/main/java/com/ktb4/team16/mulo/auth/message/AuthMessage.java`

코드가 변경되면 이 목록과 설명을 함께 갱신한다. 이 문서는 코드보다 우선하는 새로운 정책이 아니라, 현재 합의와 구현을 설명하는 안내서다. 문서와 코드가 다르면 API 명세와 팀 합의를 다시 확인한 뒤 둘을 일치시킨다.
