# 로그인 API 구현 문서

## 1. API 명세

### 로그인

`POST /api/auth/login`

로그인은 공개 엔드포인트지만 CSRF 보호 대상이다. 요청 전에 `GET /api/csrf`로 받은
CSRF Cookie와 같은 값을 `X-XSRF-TOKEN` 헤더로 보낸다.

요청 예시:

```json
{
  "email": "user@example.com",
  "password": "<password>"
}
```

성공 응답(200):

```json
{
  "message": "로그인 완료",
  "accessToken": "<access-token>"
}
```

응답에는 별도로 `refreshToken` Cookie가 설정된다. 문서의 표기값은 형식 설명용이며,
실제 비밀번호와 Token 값은 저장하거나 공유하지 않는다.

## 2. Controller → Service → Repository → DB

```text
AuthController#login
  -> AuthService#login
    -> UserRepository#findByEmailAndDeletedAtIsNull
    -> PasswordEncoder#matches
    -> JwtTokenProvider#createAccessToken / #createRefreshToken
    -> AuthService#saveRefreshToken
      -> RefreshTokenRepository#findByUser / #save
      -> MySQL refresh_tokens
```

- Controller는 요청 DTO 검증, Cookie 설정, HTTP 응답만 담당한다.
- Service는 활성 사용자 조회, BCrypt 검증, JWT 발급, Refresh Token 영속화를 담당한다.
- Repository는 사용자 조회와 Refresh Token 저장만 담당한다.
- `refresh_tokens`에는 Refresh Token 원문이 아닌 SHA-256 hexadecimal 해시만 저장한다.
  사용자는 하나의 Refresh Token 행만 가지며, 새 로그인에서 기존 행을 교체한다.

## 3. JWT 발급 및 검증

- 알고리즘: HS256
- Secret: `JWT_SECRET` 환경변수의 Base64 값. 복호화한 길이는 32바이트 이상이어야 한다.
- Claim: `sub`(사용자 ID), `iat`, `exp`, `tokenType`만 사용한다.
- Access Token 만료: 1시간
- Refresh Token 만료: 7일

보호 API 요청은 `JwtAuthenticationFilter`가 `Authorization: Bearer <access-token>`을
검증한다. 서명이 유효하더라도 `users.deleted_at` 기준 활성 사용자가 아니면
SecurityContext를 만들지 않는다. 따라서 탈퇴 사용자는 기존 Access Token이 만료되기 전에도
보호 API에 접근할 수 없다.

## 4. Token 전달과 Cookie

| 대상 | 전달 방식 | 보관·범위 |
| --- | --- | --- |
| Access Token | 로그인 JSON Body | 프론트엔드가 필요한 범위에서 관리한다. 백엔드는 Local Storage에 기록하지 않는다. |
| Refresh Token | `refreshToken` Set-Cookie | `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, host-only, 7일 |

`Secure` 속성은 `mulo.security.cookie-secure` 설정을 따른다. local 프로필은 HTTP 검증을
위해 `false`, 운영 기본값은 `true`다.

## 5. 비밀번호·오류 처리

- 회원가입 시 BCrypt로 해시한 값을 `users.password_hash`에 저장한다.
- 로그인 시 `PasswordEncoder.matches(평문, BCrypt 해시)`로 비교한다.
- 이메일 없음, 비밀번호 불일치, 탈퇴 사용자는 모두 아래와 같은 계약으로 응답한다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "errors": []
}
```

동일한 401 응답은 이메일 존재 여부가 외부에 드러나는 것을 줄인다. 빈 `email` 또는
`password`는 Bean Validation에 의해 400 `INVALID_INPUT_VALUE`로 처리한다.

## 6. 보안 설정

- Spring Security는 `STATELESS`이며 세션·기본 로그인·HTTP Basic·기본 로그아웃을 사용하지 않는다.
- `POST /api/users/signup`만 CSRF 예외다.
- `POST /api/auth/login`은 `permitAll`이지만 CSRF 예외가 아니다.
- CSRF Cookie는 JavaScript가 읽을 수 있어야 하므로 HttpOnly가 아니며, Refresh Cookie만
  HttpOnly다.
- CORS는 same-origin 기본값을 유지하며 local 프로필에서만 `http://localhost:3000`을 허용한다.

## 7. 검증 결과

- JWT 발급과 Access/Refresh 용도 분리 단위 테스트 통과
- Refresh Token 생성·교체·해시 저장 단위 테스트 통과
- AuthService의 BCrypt 비교·통합 401 단위 테스트 통과
- Controller의 Access Body·Refresh Cookie 계약 테스트 통과
- JWT 인증 필터와 기존 Security(CSRF/CORS/Stateless) 회귀 테스트 통과
- MySQL 8.4 Compose 컨테이너 Healthy 확인
- MySQL/Flyway 환경에서 `./gradlew test check` 통과
- curl로 회원가입 201, CSRF 204, 로그인 200, 실패 로그인 401,
  Refresh Token 해시 길이 64 DB 저장을 확인

## 8. Postman 사용 방법

1. `GET /api/csrf`를 실행한다.
2. 응답의 `XSRF-TOKEN` Cookie를 Cookie Jar에 유지한다.
3. `POST /api/auth/login` 요청에 `Content-Type: application/json`과
   `X-XSRF-TOKEN: <XSRF-TOKEN Cookie 값>` 헤더를 설정한다.
4. 요청 Body에 `email`, `password`를 넣어 실행한다.
5. 응답 Body의 Access Token 필드와 HttpOnly `refreshToken` Cookie를 확인한다.

## 9. 남은 개선 사항

- Refresh Token 재발급 API와 Rotation 정책
- 로그아웃 시 Refresh Token 폐기
- 로그인 rate limit, 이메일 인증, CAPTCHA 등 무분별한 로그인 시도 방어
- 운영 배포 도메인 확정 후 CORS Origin과 Cookie `Secure` 설정 재검토
