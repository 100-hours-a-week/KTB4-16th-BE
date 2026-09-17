# MULO 기초 Security

## 범위와 패키지

기술 공통 설정은 `global/config`, 필터 오류 처리는 `global/security`, 오류 DTO는
`global/error`에 둔다. 회원가입은 이후 `user`, 로그인·재발급·로그아웃은 `auth`
도메인 내부의 controller/service/repository/entity/dto 계층으로 구현한다.

현재는 Security 기반만 구현한다. 회원가입 저장, JWT 발급/검증, Refresh Token
저장·폐기 기능은 아직 없다. 테스트의 회원가입 Controller는 테스트 전용이다.

## 요청 흐름

1. CORS에서 로컬 프론트 Origin을 확인한다. 정상 preflight는 인증 없이 처리한다.
2. CSRF 필터에서 POST/PATCH/DELETE 등 안전하지 않은 메서드의 토큰을 검증한다.
3. URL 인가 규칙을 적용한다.
4. 통과한 요청만 MVC Controller로 전달된다.

| 경로 | 인가 | 응답/동작 |
|---|---|---|
| GET `/api/csrf` | 공개 | 204, XSRF-TOKEN Cookie 발급/유지, Body 없음 |
| POST `/api/users/signup` | 공개 | CSRF는 필수, 실제 회원가입 Controller는 후속 구현 |
| 나머지 `/api/**` | 인증 필요 | 인증 수단 구현 전에는 정상 인증 불가 |
| 나머지 경로 | 거부 | 백엔드가 프론트 정적 파일을 제공하는 구조는 아님 |

`permitAll`은 CSRF 면제가 아니다. CSRF가 없는 POST는 인증 검사보다 먼저 403이
반환될 수 있다. GET 보호 리소스 또는 유효한 CSRF를 가진 비인증 변경 요청은 401이다.
내부 ERROR 디스패치는 원래 오류 상태를 보존하도록 허용한다. 직접 `/error`를
호출하는 일반 요청을 공개하는 설정은 아니다.

## 환경

기본 설정은 운영 방향이다: HTTPS, same-origin, CSRF Cookie Secure=true,
SameSite=Strict, Domain 미지정(host-only), Path=/, HttpOnly=false.
CSRF Cookie는 프론트가 읽어 헤더에 넣어야 하므로 HttpOnly를 사용하지 않는다.

로컬 HTTP 개발:

먼저 `README_DEV_SETUP.md`에 따라 `.env`의 DB 환경변수를 현재 터미널에 로드한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`local`만 Secure=false이며 `http://localhost:3000`을 허용한다.
주소 변경은 `FRONTEND_ORIGIN` 환경변수로 지정한다. `localhost`와 `127.0.0.1`을
혼용하지 않는다. 허용 메서드는 GET/POST/PATCH/DELETE/OPTIONS,
허용 헤더는 Content-Type/Authorization/X-XSRF-TOKEN이다.

운영은 기본 프로필로 실행하며 local 프로필을 활성화하지 않는다.
프론트와 `/api/**`를 같은 외부 Origin으로 노출하고 리버스 프록시가 API를 전달한다.
운영에서 프론트가 별도 Origin에 배포되면 현재 설정으로 허용되지 않는다.

## 프론트 CSRF 계약

1. 앱 시작 시 GET `/api/csrf`를 호출한다.
2. `document.cookie`에서 XSRF-TOKEN 값을 읽는다.
3. 상태 변경 요청에 `X-XSRF-TOKEN` 헤더로 값을 전달한다.
4. 로컬 포트가 다른 API에는 `credentials: 'include'`를 사용한다.
5. 로그인/로그아웃에서 토큰을 초기화하도록 구현하면 이후 다시 CSRF를 발급받는다.

운영 same-origin fetch는 기본 `credentials: 'same-origin'`으로 Cookie를 전송한다.
로컬에서 포트가 다른 API로 fetch할 때는 절대 API URL을 사용해야 한다.
상대 URL `/api/...`는 개발 프록시가 없다면 프론트 서버로 향한다.

Spring Security 7의 `csrf.spa()`로 Cookie 원본 헤더와 BREACH 보호를 처리한다.
CSRF는 사용자 인증이 아니며 XSS나 탈취된 Refresh Token 자체의 재사용을 막지 않는다.

## 응답과 세션

오류는 `{ "code": "...", "message": "..." }` 형태다.

| 상태 | code | 의미 |
|---|---|---|
| 401 | UNAUTHORIZED | 인증 필요 |
| 403 | CSRF_TOKEN_INVALID | CSRF 누락 또는 불일치 |
| 403 | FORBIDDEN | 인가 거부 |

CORS 거부는 Spring CORS 처리기의 기본 403이며 위 JSON 계약과 별개다.
필터 예외는 MVC의 ControllerAdvice가 처리하지 못하므로 EntryPoint/DeniedHandler가
응답한다. STATELESS와 요청 캐시 비활성화로 인증을 세션에 저장하지 않으며,
기존 세션의 SecurityContext도 인증 근거로 사용하지 않는다.

formLogin, HTTP Basic, 기본 `/logout`은 비활성화했다.
BCryptPasswordEncoder는 users.password_hash VARCHAR(60)에 맞는 해시를 생성한다.
DB 스키마 및 Flyway migration은 변경하지 않았다.

## 후속 인증 구현

Refresh Cookie는 CSRF Cookie와 별개다. HttpOnly=true, 운영 Secure=true,
SameSite=Strict, Path=/api/auth, 7일로 구현해야 한다. 아직 발급 코드는 없다.
Access Token 1시간, Refresh Token SHA-256 DB 저장, Rotation 미적용은 기존 정책이다.
JWT 필터와 활성 사용자 확인을 추가하고 로그인/재발급/로그아웃 경로를 명시적으로
열어야 한다. 재발급은 유효한 Access Token을 필수로 요구해서는 안 된다.

## 검증

```bash
./gradlew test --tests '*SecurityIntegrationTests' --tests '*ProductionSecurityTests'
docker compose up -d --wait
./gradlew clean build
```

Security 테스트는 DB 없이 실제 필터와 실제 CSRF Cookie/Header를 검증한다.
기존 contextLoads 테스트는 MySQL/Flyway를 포함한 애플리케이션 기동을 검증한다.

## 업로드 전 확인

원격 develop에는 CI만 있어 최초 업로드에는 기존 로컬 초기 프로젝트도 포함된다.
기존 CI는 JDK 25와 checkstyleMain을 사용하지만 로컬은 Java 21이며 Checkstyle이
설정되지 않았다. CI 파일은 변경하지 않고 클라우드 담당자에게 정합성 확인을 요청한다.
전체 테스트를 CI에 추가할 경우 MySQL 준비와 DB_PASSWORD 주입도 필요하다.
작업 브랜치명은 feat/security-setup-51, 관련 이슈는 #51이다. PR은 feature를 대상으로
생성하며 merge는 사용자가 직접 수행한다.

참고: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
