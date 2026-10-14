# 요청 본문 오류 처리 구현 계획 (#67)

**목표:** `@RequestBody` 본문 파싱 실패를 공통 400 오류로, 미지원 요청 `Content-Type`을 공통 415 오류로 처리하고 둘 다 Sentry 서버 장애 수집에서 제외한다.

**기준:** `app/mulo-be` 현재 코드와 확인한 `origin/develop`. 요청 오류는 [BE #67](https://github.com/100-hours-a-week/KTB4-16th-BE/issues/67)을 기준으로 하고, PR 작업 번호는 사용자 지정 [wiki #223](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/223)을 사용한다. Java 21, Spring Boot 4.1.1.

## 근거와 결정

- Spring MVC는 `HttpMessageNotReadableException`을 400, `HttpMediaTypeNotSupportedException`을 415로 분류한다. [Spring 기본 예외 상태표](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/support/DefaultHandlerExceptionResolver.html), [RFC 9110의 400·415 정의](https://www.rfc-editor.org/rfc/rfc9110.html).
- `@RequestBody`는 Bean Validation보다 먼저 역직렬화된다. JSON을 읽지 못하면 DTO 필드 검증 처리기가 아니라 본문 읽기 예외 처리기가 필요하다. [Spring `@RequestBody` 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html).
- 전역 `@RestControllerAdvice`는 컨트롤러 간 동일한 오류 변환에 사용된다. [Spring Controller Advice 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html).
- 이슈 예시의 메시지 `잘못된 입력값입니다.`와 현재 [`ErrorCode.java`](../../../src/main/java/com/ktb4/team16/mulo/global/error/ErrorCode.java)의 `입력값을 확인해주세요.`가 다르다. 기존 공개 문구를 유지한다.
- 사용자가 미지원 미디어 타입에는 415와 새 `UNSUPPORTED_MEDIA_TYPE` 코드를 선택했다. 일반 요청 형식과 이미지 파일 형식의 오류 코드는 분리한다.

## 현재 코드 → 수정 코드

[`GlobalExceptionHandler.java`](../../../src/main/java/com/ktb4/team16/mulo/global/exception/GlobalExceptionHandler.java)의 기존 `handleInvalidParameter` 예외 목록:

```java
@ExceptionHandler({MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class})
```

수정 목록과 메서드 책임 주석:

```java
@ExceptionHandler({MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class})
// 요청 파라미터 오류와 요청 본문 파싱 실패를 공통 400 응답으로 변환한다.
```

기존에는 일반 요청의 미지원 미디어 타입을 처리하는 메서드와 코드가 없다. 추가 형태:

```java
UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),

@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
// 지원하지 않는 요청 Content-Type을 공통 415 응답으로 변환한다.
public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
        HttpMediaTypeNotSupportedException exception) {
    return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status())
            .body(ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
}
```

기존 `handleUnexpectedException`의 500 응답·Sentry 호출은 실제 미처리 예외용으로 유지한다. 4xx 전용 처리기는 이 폴백을 거치지 않는다.

## 구현 순서와 확인

- [ ] `UserControllerTest`의 `POST /api/users/signup`에 깨진 JSON, 문자열 필드의 배열, 빈 본문, `text/plain` 요청을 추가한다. 각 테스트는 상태, 공통 JSON 코드·메시지, `errors` 생략, 회원가입 서비스 미호출을 검증하고 메서드 책임 주석을 둔다.
- [ ] 테스트를 먼저 실행해 기존 500 실패를 관찰한다.
- [ ] `GlobalExceptionHandler`에 본문 읽기 예외 매핑과 미지원 미디어 타입 전용 메서드를 추가한다. `ErrorCode`에는 범용 415 코드를 추가한다.
- [ ] `GlobalExceptionHandlerSentryTest`에서 400·415 처리 시 Sentry 미호출, 기존 500 처리 시 Sentry 호출을 검증한다.
- [ ] 두 테스트 클래스를 실행한다: `./gradlew test --tests 'com.ktb4.team16.mulo.user.controller.UserControllerTest' --tests 'com.ktb4.team16.mulo.global.exception.GlobalExceptionHandlerSentryTest'`.
- [ ] `./gradlew test`와 `./gradlew clean build`를 실행한다. DB 의존 테스트에 필요한 환경을 준비하고, 실패 시 원인을 보고한다.
- [ ] 최신 `origin/develop`를 반영하고 변경 파일·Secret·Flyway·API 계약을 검토한 뒤 작업 브랜치에서 `develop`으로 PR을 만든다.

## 완료 기준

| 입력 | HTTP | `code` | Sentry |
| --- | ---: | --- | --- |
| 깨진 JSON, 잘못된 DTO 필드 타입, 빈 필수 본문 | 400 | `INVALID_INPUT_VALUE` | 미호출 |
| 미지원 요청 `Content-Type` | 415 | `UNSUPPORTED_MEDIA_TYPE` | 미호출 |
| 기존 Bean Validation 오류 | 400 | 기존 필드별 오류 유지 | 미호출 |
| 실제 미처리 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` | 호출 |

DB·Flyway·의존성은 변경하지 않는다. 415 코드 추가는 공개 API 계약 변경이므로 PR 본문에 명시하고 팀 리뷰를 받는다.
