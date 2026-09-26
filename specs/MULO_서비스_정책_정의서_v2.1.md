# MULO 서비스 정책 정의서 v2.1

> **문서 목적**  
> MULO V1 서비스의 기능·권한·예외·데이터 생명주기·운영시간·외부 연동 정책을 하나의 기준 문서(SSOT)로 관리한다.  
> 구현 시 테이블/ERD/API 문서와 본 정책이 충돌하면, **사용자가 가장 최근에 명시적으로 확정한 정책**을 우선하고 관련 설계 문서를 동기화한다.
>
> **작성일:** 2026-09-21 (KST)  
> **기준 자료**
> 1. 대화에서 사용자가 직접 확정한 최신 결정
> 2. `app/mulo-be` 현재 `feature` 작업 트리와 Flyway V1~V4
> 3. `MULO_테이블_정의서_v8.1`
> 4. `MULO_OpenAPI_v2.7`
> 5. 테이블 정의서 v7.6, 정책 정의서 v1.9, OpenAPI v2.6
> 6. API 원본 엑셀, ERD/DDL, 이전 문서는 누락 감사용으로만 사용한다.
>

> **정책 상태**
> - **확정**: 사용자가 직접 확정했거나 최신 정책으로 명시된 규칙
> - **구현됨**: 현재 `feature` 코드 또는 Flyway에서 동작이 확인되는 규칙
> - **현재 설계**: 최신 API/테이블 정의에 이미 적용 중인 V1 기준 규칙
> - **확인 필요**: 문서·구현이 다르거나 팀 결정이 필요한 규칙
> - **보류**: 후속 결정 또는 구현으로 명시적으로 미룬 규칙
> - **V1 제외**: V1 구현 범위에 포함하지 않는 규칙
> - **미확정**: 최종 결정이 아직 필요한 규칙
>
> **서비스 범위**
> - V1 서비스 지역은 **대한민국 국내 한정**이다.
> - 날짜·시간 기반 비즈니스 규칙은 **Asia/Seoul (KST, UTC+09:00)** 기준으로 판단한다.
> - 장소 지역 기준은 **법정동**이다.


## 정책 현황 요약

- V1 지역 기준: 대한민국 국내 / 법정동을 표시·집계 기준으로 사용
- Kakao 조회가 정상 성공했지만 법정동 결과가 없으면 `null/null`로 자물쇠 생성을 허용
- V1 서비스 시간 기준: `Asia/Seoul`
- V1 제외: `record_drafts`, AI 기억 검색, Spotify 계정 저장
- 신규 V1 범위: 법정동 코드 기반 대시보드 그룹, 기상청 단기예보 캐시, `GET /api/weather`
- 미정 항목은 Open Question으로 유지하며 상태 코드·오류 코드를 임의 생성하지 않는다.

### 현재 구현 스냅샷

| 영역 | 현재 `feature` 상태 | 계약 상태 |
|---|---|---|
| 인증 | 로그인·재발급·로그아웃 구현 | 구현됨 |
| 회원 | 회원가입·내 정보·닉네임·비밀번호 변경 구현 | 구현됨 |
| 장소 | 내 장소 지도 조회 구현 | 구현됨 |
| 날씨 | 기상청 격자 캐시 기반 조회 구현 | 구현됨 |
| 월간 리포트 | 생성·집계·목록 조회가 현재 작업 트리에 구현 | 목록 구현됨, 상세 미구현 |
| 나머지 OpenAPI v2.7 API | 계약은 보존하되 현재 저장소 Controller 없음 | 설계 확정 또는 미구현 |


## 공통·문서 원칙

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `GLB-001` | **현재 설계** | 정책 기준 문서 우선순위 | 사용자의 최신 확정 결정, 현재 `feature` 구현과 Flyway, 테이블 정의서 v8.1, OpenAPI v2.7을 우선한다. 과거 문서는 누락 감사용으로만 사용한다. | 본 문서 기준 자료 / 통합 문서 생성 설계 |
| `GLB-002` | **현재 설계** | 미확정 사항 처리 | 문서 간 충돌하거나 현재 자료에서 결정되지 않은 항목은 임의 확정하지 않고 `확인 필요`로 분리한다. | 테이블 정의서 6차 서두 |
| `GLB-003` | **현재 설계** | 활성 사용자 기준 | 인증이 필요한 API는 원칙적으로 인증된 활성 사용자를 대상으로 한다. 탈퇴 사용자는 정상 인증 사용자로 취급하지 않는다. | API 명세 상세2차 AUTH/USER 전반 |
| `GLB-004` | **현재 설계** | 리소스 존재 여부 은닉 | 소유권 또는 접근권한이 없는 일부 개인 리소스는 존재 여부가 노출되지 않도록 미존재와 동일한 404로 처리한다. | API 명세 상세2차 record/friend/match/chat/notification |
| `GLB-005` | **확정** | 국내 서비스 범위와 법정동 미확인 | MULO V1은 대한민국 국내에서만 자물쇠 생성을 지원한다. 국내 여부와 Kakao의 법정동 결과 존재 여부는 분리한다. Kakao 조회가 정상 성공했지만 `region_type=B` 결과가 없으면 서비스 범위 밖으로 간주하지 않고 `legal_dong_code`, `legal_dong_name`을 모두 `null`로 저장해 생성을 허용한다. | 사용자 확정 2026-09-21 / `PLACE-005` |
| `GLB-006` | **확정** | 서비스 기준 시간대 | `오늘`, 투표 일자, 23:00 매칭 종료, 00:00 채팅방 삭제, 월말 판단 등 모든 비즈니스 날짜·시간은 `Asia/Seoul`(KST, UTC+09:00) 기준으로 계산한다. DB의 물리적 저장 timezone과는 별개의 서비스 규칙이다. | 사용자 확정 |

## 회원·인증

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `AUTH-001` | **확정** | Access/Refresh 유효기간 | Access Token 유효기간은 1시간, Refresh Token 유효기간은 7일이다. | API 명세 상세2차 정책 요약 R3 |
| `AUTH-002` | **확정** | Refresh Token 전달 방식 | Refresh Token은 HttpOnly Cookie로 전달한다. | API 명세 상세2차 정책 요약 R29 / 상세 R4,R22 |
| `AUTH-003` | **확정** | Refresh Token 저장 방식 | 서버 DB에는 Refresh Token 원문 대신 SHA-256 해시값을 저장한다. | API 명세 상세2차 정책 요약 R29 / refresh_tokens |
| `AUTH-004` | **확정** | Refresh Token Rotation | Refresh Token Rotation은 적용하지 않으며 유효한 RT로 Access Token만 새로 발급한다. | API 명세 상세2차 정책 요약 R2 |
| `AUTH-005` | **현재 설계** | 사용자당 Refresh Token 수 | `refresh_tokens.user_id`는 UNIQUE이며 한 사용자는 동시에 최대 하나의 Refresh Token 레코드를 가진다. | 테이블 정의서 6차 §2 |
| `AUTH-006` | **현재 설계** | 로그인 응답 | 로그인 성공 시 Access Token은 응답 Body로 반환하고 Refresh Token은 HttpOnly Cookie로 발급한다. | API 명세 상세2차 R4,R9 |
| `AUTH-007` | **현재 설계** | 로그인 실패 통합 | 존재하지 않는 이메일, 비밀번호 불일치, 탈퇴 사용자는 동일한 401 `INVALID_CREDENTIALS`로 처리한다. | API 명세 상세2차 R7 |
| `AUTH-008` | **확정** | 로그아웃 멱등성 및 클라이언트 Access Token 폐기 | 로그아웃은 멱등적으로 처리한다. 로그아웃 시 클라이언트는 보유한 Access Token을 즉시 폐기하고 로그아웃 상태로 전환한다. 서버 로그아웃 처리가 실패하더라도 클라이언트의 로컬 로그아웃은 수행한다. | 사용자 확정 / API 명세 상세2차 |
| `AUTH-009` | **확정** | 로그아웃 서버 처리 | 서버는 로그아웃 시 Refresh Token을 revoke하고 Refresh Token Cookie를 만료한다. 별도 Access Token blacklist 정책은 두지 않고, 클라이언트가 Access Token을 즉시 폐기하는 방식으로 처리한다. | 사용자 확정 / API 명세 상세2차 |
| `AUTH-010` | **현재 설계** | Refresh 인증 실패 통합 | Refresh Token 없음·만료·서명 오류·DB 불일치·revoke 등은 401 `INVALID_REFRESH_TOKEN`으로 통합한다. | API 명세 상세2차 R20,R22 |
| `AUTH-011` | **현재 설계** | 비밀번호 저장 | 비밀번호 원문은 저장하지 않고 bcrypt 해시값을 저장한다. | 테이블 정의서 6차 §1 |
| `AUTH-012` | **현재 설계** | 회원가입 중복 기준 | 활성 사용자 기준 이메일과 닉네임 중복을 허용하지 않으며 탈퇴 회원의 값은 재사용할 수 있다. | API 명세 상세2차 R13,R15 / users generated columns |
| `AUTH-013` | **현재 설계** | 비밀번호 확인 필드 | 회원가입의 `passwordConfirm`, 비밀번호 변경의 `newPasswordConfirm`은 프론트에서만 검증하고 API Body에는 포함하지 않는다. | API 명세 상세2차 R15,R36,R43 |
| `AUTH-014` | **현재 설계** | 비밀번호 정책 | 비밀번호는 영문 대소문자, 숫자, 특수문자를 포함한 8~16자 정책을 적용한다. | API 명세 상세2차 R12,R39 |
| `AUTH-015` | **확정** | 닉네임 형식 정책 | 닉네임은 필수이며 **2자 이상 10자 이하**여야 한다. **영문(A-Z, a-z), 완성형 한글(가-힣), 숫자(0-9)**만 허용하고 띄어쓰기와 특수문자는 허용하지 않는다. 활성 사용자가 사용 중인 닉네임과 중복될 수 없으며, 탈퇴 사용자의 닉네임은 재사용할 수 있다. 구현 정규식 기준은 `^[A-Za-z0-9가-힣]{2,10}$`이다. | 사용자 확정 / 기존 AUTH 설계 |
| `AUTH-016` | **현재 설계** | 닉네임 동일값 변경 | 현재 닉네임과 동일한 값으로 변경 요청 시 409 `SAME_NICKNAME`이다. | API 명세 상세2차 R28,R33 |
| `AUTH-017` | **현재 설계** | 비밀번호 변경 검증 | 현재 비밀번호 일치가 필요하며 새 비밀번호가 현재 비밀번호와 같으면 409 `SAME_PASSWORD`이다. | API 명세 상세2차 R36-R43 |
| `AUTH-018` | **현재 설계** | 비밀번호 변경 후 세션 | 비밀번호 변경 성공 후 현재 로그인 세션은 유지한다. | API 명세 상세2차 R36,R43 |
| `AUTH-019` | **현재 설계** | 회원 탈퇴 방식 | 회원 탈퇴는 `users.deleted_at`을 설정하는 soft delete이며 현재 비밀번호 확인이 필요하다. | API 명세 상세2차 R44-R49 |
| `AUTH-020` | **현재 설계** | 탈퇴 시 인증 종료 | 회원 탈퇴 시 Refresh Token을 revoke하고 Cookie를 만료해 현재 세션을 종료한다. | API 명세 상세2차 R44,R49 |
| `AUTH-021` | **현재 설계** | 외부 사용자 식별자 | 외부 API에서도 내부 `user_id`를 `userId`로 사용한다. | API 명세 상세2차 정책 요약 R5 / 상세 R24 |
| `AUTH-022` | **확정** | 회원 탈퇴 후 연결 데이터 보존 | 회원 탈퇴는 사용자 soft delete이므로 자물쇠·사진·친구·투표·매칭·채팅·알림·리포트 등 연결 서비스 데이터는 원칙적으로 보존한다. 각 기능의 별도 만료/삭제 정책이 있는 데이터는 그 정책을 따른다. | 사용자 확정 |
| `AUTH-023` | **확정** | 다중 기기 로그인 미지원 | V1은 동시에 여러 Refresh 세션을 유지하는 다중 기기 로그인을 지원하지 않는다. 사용자당 Refresh Token은 최대 1개만 유지하며, 새 로그인 성공 시 기존 `refresh_tokens` 레코드는 새 Refresh Token 상태로 교체한다. | 사용자 확정 / `refresh_tokens.user_id` UNIQUE |
| `AUTH-024` | **확인 필요** | Refresh Token Cookie 속성 | Refresh Token Cookie는 `HttpOnly=true`를 적용하고 운영 환경에서는 `Secure=true`를 적용한다. Cookie 전송 경로는 `Path=/api/auth`로 제한한다. `SameSite`의 최종 정책은 실제 Frontend/Backend 배포 site 구조에 따라 추후 확정하며, 현재 로그인·로그아웃·CSRF 구현은 `SameSite=Strict`를 유지한다. | 사용자 확정 2026-09-21 / `AuthController` / `SecurityConfig` |
| `AUTH-025` | **현재 설계** | CORS와 Cookie 정책 분리 | CORS는 cross-origin 요청 허용 정책이고 Cookie의 `SameSite`/`Secure`/`Path`를 대체하지 않는다. Frontend와 Backend가 cross-origin이면 서버는 실제 Frontend Origin만 허용하고 credentials를 허용하며, `*` Origin과 credentials를 함께 사용하지 않는다. Frontend도 credentials 포함 요청을 사용한다. | 최신 AUTH 검토 |


## 사용자·대표 음악 장르

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `GENRE-001` | **확정** | 대표 음악 장르 | AI가 사용자의 음악 기록을 분석해 대표 음악 장르 1개를 `users.music_genre`에 저장한다. | API 명세 상세2차 정책 요약 R33 / 테이블 정의서 §1 |
| `GENRE-002` | **확정** | 장르 미결정 상태 | 대표 장르가 아직 결정되지 않은 사용자는 `music_genre=NULL`일 수 있다. | 테이블 정의서 6차 §1 |
| `GENRE-003` | **확정** | 장르 미결정 사용자의 투표 | `users.music_genre IS NULL`인 사용자는 취향 투표에 참여할 수 없다. | API 명세 상세2차 정책 요약 R33 |
| `GENRE-004` | **확정** | 대표 장르 미결정 API 응답 | `users.music_genre IS NULL`인 사용자가 오늘 투표 조회 또는 투표 제출 API에 직접 접근하면 `403 Forbidden` / `MUSIC_GENRE_REQUIRED`로 처리한다. | 사용자 확정 |

## 장소·지도·대시보드

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `PLACE-003` | **확정** | 법정동 저장 | `places.legal_dong_code`, `places.legal_dong_name`을 한 쌍으로 저장한다. Kakao Maps SDK `coord2RegionCode()`의 `region_type=B` 결과에서 `code → legal_dong_code`, `region_3depth_name → legal_dong_name`으로 매핑한다. | 사용자 확정 / Kakao 법정동 응답 매핑 |
| `PLACE-004` | **확정** | 위치 선택 책임 | 프론트는 브라우저 Geolocation API로 최초 위치를 얻고 Kakao Maps SDK 지도에서 사용자가 수정한 최종 `latitude`/`longitude`를 확정한다. | 사용자 확정 |
| `PLACE-005` | **확정** | 법정동 미확인 허용 | Kakao Maps SDK 호출이 정상 성공했지만 `region_type=B` 결과가 없는 경우 `legal_dong_code`, `legal_dong_name`을 둘 다 NULL로 전달·저장하고 자물쇠 생성을 허용한다. 둘 중 하나만 NULL인 상태는 허용하지 않는다. | 사용자 확정 |
| `PLACE-006` | **확정** | 대시보드 그룹 기준 | `legal_dong_code`가 있으면 해당 코드로 그룹화하고 `legal_dong_name`을 표시한다. `legal_dong_code IS NULL`인 데이터는 하나의 미확인 그룹으로 묶고 UI에 `확인할 수 없음`으로 표시한다. | 사용자 확정 |
| `PLACE-008` | **현재 설계** | 지도 조회 영역 | viewport는 SW/NE bounding box(`swLat/swLng/neLat/neLng`)로 전달한다. | API 명세 상세2차 |
| `PLACE-009` | **확정** | 인기 자물쇠 조회 기간 | 홈의 인기 자물쇠 지도/목록은 조회 시점 기준 최근 7일 이내 생성된 삭제되지 않은 자물쇠만 대상으로 한다. 최근 7일 내 자물쇠가 없는 장소는 인기 자물쇠 마커 대상에서 제외한다. | 사용자 확정 |
| `PLACE-010` | **확정** | 지도 마커·클러스터 | 개별 지도 마커 1개는 `place` 1개다. 지도 축소로 여러 마커가 프론트에서 하나의 클러스터로 묶이면 클러스터가 포함한 모든 `placeId`를 모아 다중 장소 조회 API에 전달한다. | 사용자 확정 |
| `PLACE-011` | **현재 설계** | 인기 장소 공개 범위 | 인기 장소 및 장소별 인기 음악은 비로그인 조회가 가능하다. | API 명세 상세2차 |
| `PLACE-012` | **확정** | 장소별 인기 음악 집계·정렬 | 개별 마커 또는 클러스터 선택 시 전달된 `placeIds` 전체의 최근 7일 이내 삭제되지 않은 자물쇠만 음악별 저장 횟수로 집계한다. 음악별 `count DESC`로 정렬하고 동률이면 해당 음악의 가장 최근 Record 생성 시각이 최신인 음악을 우선한다. | 사용자 확정 2026-09-21 / 팀원 OpenAPI v2.4 |
| `PLACE-013` | **확정** | 내 자물쇠 전체 조회 | 내 자물쇠 지도/목록에는 7일 제한을 적용하지 않는다. 현재 사용자의 삭제되지 않은 모든 자물쇠가 조회 대상이다. | 사용자 확정 |
| `PLACE-014` | **현재 설계** | 특정 장소 내 내 기록 | 삭제되지 않은 기록만 최신순으로 조회하고 같은 장소·음악 반복 기록을 허용한다. | API 명세 상세2차 |
| `PLACE-015` | **확정** | 장소 표시 명칭 | 장소 관련 화면에는 `legal_dong_name`을 표시한다. `legal_dong_name=NULL`이면 UI에서 `확인할 수 없음`으로 표시하며 API/DB에는 해당 문구를 저장하지 않는다. | 사용자 확정 |
| `PLACE-016` | **확정** | place 재사용 기준 | latitude와 longitude가 모두 정확히 동일한 기존 `place`가 있는 경우에만 재사용한다. 근접 좌표는 기존 `place`로 판단하지 않으며, 동일 좌표가 없으면 새로운 `place`를 생성한다. | 사용자 확정 |
| `PLACE-017` | **확정** | 자물쇠 생성 위치 요청값 | 프론트는 최종 `latitude`, `longitude`, `legalDongCode`, `legalDongName`을 `POST /api/records`에 전달한다. 백엔드는 자물쇠 생성 과정에서 동일 좌표로 Kakao 법정동 API를 재호출하지 않는다. | 사용자 확정 |
| `PLACE-018` | **확정** | 프론트 Kakao 조회 실패 | Kakao Maps SDK 법정동 조회 호출 자체가 실패한 경우 `null/null` 법정동으로 대체하지 않는다. 프론트에서 생성 요청을 중단하거나 재시도한다. | 사용자 확정 |
| `PLACE-019` | **확정** | 백엔드 법정동 입력 검증 | 백엔드는 `legalDongCode`/`legalDongName`이 둘 다 값이 있거나 둘 다 NULL인지 검증한다. 값이 있는 `legalDongCode`는 카카오 법정동 코드 형식에 맞는 문자열인지 검증한다. | 사용자 확정 |
| `PLACE-020` | **확정** | 클러스터·장소 인기 음악 조회 | `POST /api/places/popular-tracks/search` Body에 `placeIds`를 전달한다. ID 1개는 개별 마커, 여러 개는 클러스터를 의미하며 최근 7일 이내 삭제되지 않은 자물쇠를 합산한다. Cursor, `size`, TOP N 제한은 적용하지 않는다. | 사용자 확정 2026-09-21 / 팀원 OpenAPI v2.4 |
| `PLACE-021` | **확정** | 클러스터·장소 내 자물쇠 조회 | `POST /api/users/me/records/search` Body에 `placeIds`와 선택적 `cursor`를 전달한다. ID 1개와 여러 개를 동일 API로 처리하며 현재 사용자의 삭제되지 않은 모든 자물쇠를 최신순으로 조회한다. 7일 제한은 없다. | 사용자 확정 2026-09-21 / 팀원 OpenAPI v2.4 |
| `PLACE-022` | **확정** | 인기 마커 개수 | 인기 자물쇠 지도 마커의 `recordsCount`는 해당 장소의 최근 7일 이내 삭제되지 않은 자물쇠 수다. | 사용자 확정 |
| `PLACE-023` | **확정** | 내 자물쇠 마커 개수 | 내 자물쇠 지도 마커의 `myRecordsCount`는 해당 장소에 속한 현재 사용자의 전체 삭제되지 않은 자물쇠 수다. 7일 제한은 없다. | 사용자 확정 |
| `PLACE-024` | **확정** | 사용자 자물쇠 지역 폴더 | 별도 dashboard 리소스를 만들지 않고 현재 사용자의 `records`를 `places.legal_dong_code` 기준으로 그룹화한다. 최초 진입에서는 지역 그룹만 반환하며 자물쇠 목록 preview는 포함하지 않는다. 폴더 표시명은 `legal_dong_name`이며 NULL 그룹은 UI에서 `확인할 수 없음`으로 표시한다. 지역 그룹은 각 그룹의 최신 활성 Record `createdAt` 내림차순으로 정렬한다. | 사용자 확정 |
| `PLACE-025` | **확정** | 지역 폴더 자물쇠 목록 | 지역 폴더 선택 시 별도 `GET /api/records`로 해당 `legal_dong_code`에 속한 현재 사용자의 삭제되지 않은 모든 자물쇠를 조회한다. `legalDongCode=UNKNOWN`은 `legal_dong_code IS NULL` 그룹을 의미한다. 목록은 `createdAt DESC, recordId DESC` Cursor 방식이며 서버 페이지 크기는 20개로 고정한다. 자물쇠 선택 시 기존 `GET /api/records/{recordId}` 상세 조회를 사용한다. | 사용자 확정 |
| `PLACE-026` | **확정** | REST 경로 | 사용자 자물쇠 지역 그룹은 `GET /api/records/regions`, 그룹 내 자물쇠 목록은 `GET /api/records`로 제공한다. `GET /api/records`는 인증된 현재 사용자의 records 컬렉션을 의미한다. | 사용자 확정 |
| `PLACE-027` | **확정** | 지역 그룹 API의 미확인 표현 | DB의 `legal_dong_code`, `legal_dong_name`이 모두 NULL인 Record 그룹은 `GET /api/records/regions` 응답에서만 `legalDongCode=UNKNOWN`, `legalDongName=위치 정보 없음`으로 반환한다. `UNKNOWN`과 표시 문구는 DB에 저장하지 않는다. | 사용자 확정 |

## 음악

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `MUSIC-001` | **현재 설계** | 음악 검색 프록시 | 음악 검색은 백엔드가 Spotify Search API를 프록시하며 검색 결과에 `externalTrackId`를 반환한다. | API 명세 상세2차 R82-R88 |
| `MUSIC-002` | **현재 설계** | 검색 결과 미저장 허용 | 검색 결과의 음악이 MULO DB에 아직 존재하지 않아도 결과에 포함할 수 있다. | API 명세 상세2차 R82 |
| `MUSIC-003` | **현재 설계** | 검색 빈 결과 | 검색 결과가 없어도 정상 요청이면 200과 빈 배열을 반환한다. | API 명세 상세2차 R83 |
| `MUSIC-004` | **현재 설계** | MusicTrack 조회·생성 | 자물쇠 생성 시 요청의 음악 메타데이터를 사용한다. `externalTrackId`가 기존 `music_tracks`에 있으면 재사용하고, 없으면 요청값으로 저장한다. 자물쇠 생성 과정에서는 Spotify API를 재호출하지 않는다. | V1 확정 정책 |
| `MUSIC-005` | **현재 설계** | 음악 외부 ID 유일성 | `music_tracks.external_track_id`는 외부 음악 식별자로 UNIQUE하게 관리한다. | 테이블 정의서 6차 §4 |
| `MUSIC-006` | **현재 설계** | 음악 수정시각 | `music_tracks.updated_at`은 생성 시 NULL이며 실제 메타데이터 수정이 발생할 때 최종 수정 시각을 기록한다. | 테이블 정의서 6차 수정 요약/§4 |

## 사진·임시 업로드

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `UPLOAD-001` | **확정** | 임시 업로드 저장소 | 실제 임시 이미지는 Google Cloud Storage에 저장하고 `uploads`에는 소유 사용자·위치·MIME·크기 메타데이터를 저장한다. | API 명세 상세2차 정책 요약 R12 |
| `UPLOAD-002` | **확정** | 업로드 유효기간 | 임시 업로드는 생성 시점부터 1시간 유효하다. | API 명세 상세2차 정책 요약 R12 |
| `UPLOAD-003` | **확정** | uploadId 재사용 | 유효시간 내 같은 `uploadId`를 사진 기반 음악 추천과 최종 자물쇠 생성에 재사용한다. | API 명세 상세2차 정책 요약 R12 |
| `UPLOAD-004` | **확정** | 업로드 성공 후 삭제 | 자물쇠 생성 성공 시 `record_photos`에 최종 사진 정보를 저장한 뒤 해당 `uploads` 레코드를 삭제한다. | API 명세 상세2차 정책 요약 R12 |
| `UPLOAD-005` | **확정** | 미연결 업로드 정리 | 1시간 내 자물쇠에 연결되지 않은 업로드는 `uploads` 레코드와 GCS 임시 파일 cleanup 대상이다. | 테이블 정의서 6차 §7 / API 명세 상세2차 R96 |
| `UPLOAD-006` | **현재 설계** | 업로드 포맷/용량 | JPG, JPEG, PNG, HEIC, WebP를 허용하며 최대 10MB이다. | Issue #123 구현 정책 |
| `UPLOAD-007` | **현재 설계** | 업로드 소유권 은닉 | 존재하지 않음·다른 사용자 소유·1시간 만료·cleanup 완료 업로드는 모두 404 `UPLOAD_NOT_FOUND`로 처리한다. | API 명세 상세2차 R101,R104,R110 |
| `UPLOAD-008` | **보류** | Presigned URL | V1은 백엔드가 파일을 받아 GCS에 업로드하는 방식을 유지하며 Presigned URL 전환은 보류한다. | API 명세 상세2차 정책 요약 R13 |
| `PHOTO-001` | **현재 설계** | 최종 사진 수 | 자물쇠 생성 완료 시 사진은 정확히 1장 연결되며 `record_photos.record_id`는 1:1로 관리한다. | 테이블 정의서 6차 §5-6 / API 명세 상세2차 R106 |
| `PHOTO-002` | **현재 설계** | 최종 사진 저장 | 최종 이미지 파일은 GCS에 저장하고 DB에는 이미지 위치와 파일 메타데이터를 저장한다. | 테이블 정의서 6차 §6 |
| `PHOTO-003` | **현재 설계** | 사진 기반 음악 추천 | 유효한 본인 `uploadId`의 사진을 AI가 분석해 음악을 최대 3곡 추천한다. | API 명세 상세2차 정책 요약 R14 / R98-R104 |
| `PHOTO-004` | **확정** | 최종 사진 보존 | 사용자 탈퇴 또는 자물쇠 soft delete 이후에도 이미 최종 자물쇠에 연결된 GCS 사진과 `record_photos` 메타데이터는 보존한다. | 사용자 확정 |

## 자물쇠

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `RECORD-001` | **현재 설계** | 생성 필수값 | 자물쇠 생성에는 위도, 경도, 음악 externalTrackId, moodScore, 유효한 uploadId가 필수이며 comment는 선택이다. | API 명세 상세2차 R106 |
| `RECORD-002` | **현재 설계** | 코멘트 길이 | 코멘트는 선택값이며 최대 80자이다. | API 명세 상세2차 R106 / 테이블 정의서 §5 |
| `RECORD-003` | **현재 설계** | 기분 점수 | `mood_score`는 -50~50 범위 정수이며 화면 초기값은 0이지만 요청 Body에 명시적으로 전달한다. | 테이블 정의서 §5 / API 명세 상세2차 R107-R108 |
| `RECORD-004` | **현재 설계** | 날씨 자동 조회 | 날씨와 기온은 클라이언트가 전달하지 않고 백엔드가 자물쇠 생성 시 자동 조회한다. | API 명세 상세2차 R106,R114 |
| `RECORD-005` | **현재 설계** | 날씨 내부 분류 | 외부 날씨 원문은 MULO 내부 날씨 분류값으로 변환해 저장한다. | API 명세 상세2차 정책 요약 R9 |
| `RECORD-006` | **현재 설계** | 날씨 장애 허용 | 최신 발표본 조회와 허용된 직전 발표본 fallback 모두 실패해 날씨/기온을 확보하지 못한 경우에도 자물쇠 생성 전체 실패로 처리하지 않고 날씨·기온을 NULL로 저장한다. | 사용자 확정 / API 명세 상세2차 |
| `RECORD-007` | **현재 설계** | 중복 기록 허용 | 같은 사용자·장소·음악 조합의 반복 기록을 허용한다. | API 명세 상세2차 R114 |
| `RECORD-008` | **현재 설계** | 상세 조회 권한 | 자물쇠 상세는 소유자 또는 요청 시점에 현재 친구 관계인 사용자만 조회할 수 있다. | API 명세 상세2차 R115-R120 |
| `RECORD-009` | **현재 설계** | 친구 권한 재검증 | 친구 전용 자원 접근은 과거 관계가 아니라 요청 시점의 현재 `friendship`을 매번 재검증한다. | API 명세 상세2차 R115,R120,R180-R184 |
| `RECORD-010` | **현재 설계** | 상세 404 은닉 | 미존재·soft delete·조회 권한 없음은 동일한 404 `RECORD_NOT_FOUND`로 처리한다. | API 명세 상세2차 R118-R120 |
| `RECORD-011` | **현재 설계** | 수정 범위 | 생성 후 수정 가능한 자물쇠 필드는 `comment`뿐이다. | API 명세 상세2차 R121-R128 |
| `RECORD-012` | **현재 설계** | 코멘트 삭제 | `comment=null`이면 기존 코멘트를 삭제한다. Body에서 comment 키 자체가 누락되면 400이다. | API 명세 상세2차 R121-R128 |
| `RECORD-013` | **현재 설계** | 자물쇠 삭제 | 자물쇠 삭제는 소유자만 가능하며 `records.deleted_at`을 설정하는 soft delete이다. | API 명세 상세2차 R129-R134 |
| `RECORD-014` | **현재 설계** | 삭제 404 은닉 | 미존재·이미 삭제·타인 소유 기록은 동일한 404 `RECORD_NOT_FOUND`로 처리한다. | API 명세 상세2차 R132,R134 |
| `RECORD-015` | **현재 설계** | updated_at | 자물쇠 `updated_at`은 생성 시 NULL이고 실제 수정 발생 시 최종 수정 시각을 기록한다. | 테이블 정의서 6차 수정 요약/§5 |

## 날씨·기상청 캐시

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `WTH-001-POL` | **확정** | 데이터 원천 | 기상청 단기예보 `getVilageFcst`를 사용한다. | 동기화 설계 §3.2 |
| `WTH-002-POL` | **확정** | 캐시 조회 순서 | 위·경도를 격자(nx,ny)로 변환하고 동일 KST 날짜의 격자 캐시를 조회한다. 목표 예보시각 행이 있으면 반환하고, 당일 캐시가 전혀 없을 때만 기상청을 호출한다. | 사용자 확정 / 캐시 설계 |
| `WTH-003-POL` | **확정** | 지원 시간 | 현재 시간대와 미래 예보를 지원한다. 서버 KST의 현재 시간대 시작보다 이른 요청은 과거로 보고 `400 INVALID_WEATHER_REQUEST_TIME`으로 처리한다. | 사용자 확정 / 캐시 설계 §4.1 |
| `WTH-004-POL` | **확정** | 저장 정보 | 예보시각, 기온, 최종 날씨 상태, 발표 기준시각, 저장시각을 저장한다. | 동기화 설계 §3.2 |
| `WTH-005-POL` | **확정** | 저장 제외 | 풍속, 습도, 강수확률, 원시 PTY/SKY는 DB와 API 응답에 저장/노출하지 않는다. | 동기화 설계 §3.2 |
| `WTH-006-POL` | **확정** | 날씨 상태 | `CLEAR`, `CLOUDY`, `OVERCAST`, `RAIN`, `SNOW`, `RAIN_SNOW`, `SHOWER` 7개만 사용하며 PTY가 SKY보다 우선한다. | 동기화 설계 §3.3 |
| `WTH-007-POL` | **확정** | 레코드 생성 장애 처리 | 최신 발표본 조회와 허용된 직전 발표본 fallback 모두 실패해 날씨를 확보하지 못해도 자물쇠 생성은 가능하며 `weather_condition`, `temperature`를 NULL로 저장한다. | 사용자 확정 / 동기화 설계 §3.2/§5 |
| `WTH-008-POL` | **확정** | 추천 생성 장애 처리 | 추천 플레이리스트 생성에서는 날씨·기온이 필수이며 최신 발표본 조회와 허용된 직전 발표본 fallback 모두 실패한 경우 `502 WEATHER_API_ERROR`로 생성하지 않는다. | 사용자 확정 / 동기화 설계 §3.4/§5 |
| `WTH-009-POL` | **확정** | 날씨 조회 API | `GET /api/weather`를 제공하고 `latitude`, `longitude`, `at`을 필수로 받으며 `forecastAt`, `temperature`, `weatherCondition`만 반환한다. | 동기화 설계 §5 |
| `WTH-010-POL` | **확정** | 예보시각 1시간 단위 | 날씨 예보는 KST 기준 1시간 단위의 `forecast_at`으로 조회·캐시한다. | 사용자 확정 |
| `WTH-011-POL` | **확정** | 가장 가까운 예보시각 선택 | 요청 시각의 분이 `00~29`이면 현재 정각, `30~59`이면 다음 정각을 사용한다. 예: `15:10 → 15:00`, `15:50 → 16:00`. | 사용자 확정 |
| `WTH-012-POL` | **확정** | 1회 호출 다중 시간대 캐시 | `getVilageFcst` 1회 호출 시 응답의 목표 예보시각 이후 시간대를 파싱해 `weather_grid_forecasts`에 일괄 저장한다. | 사용자 확정 / 기상청 단기예보 응답 구조 |
| `WTH-013-POL` | **확정** | 일 단위 최초 성공 캐시 고정 | 동일 KST 날짜·격자의 최초 성공 예보 묶음을 유지한다. 같은 날 새 발표본이 공개되어도 갱신하거나 외부 API를 다시 호출하지 않는다. | 사용자 확정 |
| `WTH-014-POL` | **확정** | 최초 호출 발표 회차 fallback | 최초 적재 시 목표 예보시각을 포함하는 가장 최근 후보 회차를 시도하고, 실패·미포함이면 바로 이전 후보 회차를 한 번만 시도한다. | 사용자 확정 |
| `WTH-015-POL` | **확정** | 당일 캐시 누락 처리 | 당일 격자 캐시는 있으나 요청 목표 시간대 행이 없으면 외부 API를 다시 호출하지 않고 `502 WEATHER_API_ERROR`로 처리한다. | 사용자 확정 |

## 친구

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `FRIEND-001` | **현재 설계** | 친구 관계 방향성 | friendship은 방향 없는 관계로 사용자 ID를 low/high로 정규화해 한 사용자 쌍당 한 행만 저장한다. | 테이블 정의서 6차 §10 |
| `FRIEND-002` | **현재 설계** | 친구 요청 pending 전용 | `friend_requests`에는 현재 대기 중인 요청만 저장한다. | 테이블 정의서 6차 §9 |
| `FRIEND-003` | **현재 설계** | 친구 요청 완료 후 삭제 | 수락 시 friendship을 생성하고 요청을 삭제하며, 거절/취소 시 요청만 물리 삭제한다. | API 명세 상세2차 R170-R179 / 테이블 정의서 §9 |
| `FRIEND-004` | **현재 설계** | 역방향 요청 자동 친구 | A→B pending 중 B→A 요청이 들어오면 새 요청을 만들지 않고 기존 pending을 삭제한 뒤 즉시 friendship을 생성한다. | API 명세 상세2차 R160-R169 / 테이블 정의서 §9 |
| `FRIEND-005` | **현재 설계** | 자기 자신 요청 금지 | 자기 자신에게 친구 요청할 수 없다. | API 명세 상세2차 R165 / 테이블 정의서 §9 |
| `FRIEND-006` | **현재 설계** | 이미 친구 요청 금지 | 이미 친구라면 새 친구 요청을 생성하지 않는다. | API 명세 상세2차 R166,R169 |
| `FRIEND-007` | **현재 설계** | 동일 방향 중복 요청 금지 | 동일 방향 pending 친구 요청이 존재하면 409로 처리한다. | API 명세 상세2차 R167,R169 |
| `FRIEND-008` | **현재 설계** | 친구 수락 권한 | 친구 요청의 수신자만 해당 요청을 수락할 수 있다. | API 명세 상세2차 R170-R174 |
| `FRIEND-009` | **현재 설계** | 친구 거절/취소 권한 | 수신자가 DELETE하면 거절, 발신자가 DELETE하면 취소이며 제3자는 404 처리한다. | API 명세 상세2차 R175-R179 |
| `FRIEND-010` | **현재 설계** | 친구 삭제 | friendship의 어느 한쪽 사용자든 관계를 물리 삭제할 수 있으며 한 행 삭제로 양쪽 관계가 해제된다. | API 명세 상세2차 R180-R184 / 테이블 정의서 §10 |
| `FRIEND-011` | **현재 설계** | 친구 목록 정렬 | 친구 목록은 닉네임 오름차순, 동률은 userId 오름차순으로 정렬한다. | API 명세 상세2차 정책 요약 R17 |
| `FRIEND-012` | **현재 설계** | 친구 목록 페이지 | 친구/받은 요청/보낸 요청은 Cursor 기반이며 기본 size 20이다. 빈 목록은 200과 빈 배열이다. | API 명세 상세2차 R136-R159 |

## 월간 리포트

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `REPORT-001` | **현재 설계** | 리포트 생성 대상 | 해당 월에 기록이 1건 이상 존재하는 경우에만 월간 리포트를 생성한다. | API 명세 상세2차 R186-R190 |
| `REPORT-002` | **현재 설계** | 월간 스냅샷 | 월 종료 후 생성된 리포트는 snapshot으로 보존하며 이후 원본 자물쇠 수정·삭제가 있어도 기존 결과를 재계산하지 않는다. | API 명세 상세2차 R190,R198 / 테이블 정의서 §11 |
| `REPORT-003` | **현재 설계** | 월별 유일성 | 사용자당 같은 연·월 리포트는 최대 1개이다. | 테이블 정의서 6차 §11 |
| `REPORT-004` | **현재 설계** | 리포트 목록 | 생성된 모든 월을 최신 연/월 순으로 반환하며 페이지네이션은 사용하지 않는다. | API 명세 상세2차 R186-R190 |
| `REPORT-005` | **현재 설계** | 리포트 소유권 | 월간 리포트 상세는 소유 사용자만 조회할 수 있으며 다른 사용자 리포트는 노출하지 않는다. | API 명세 상세2차 R191-R198 |
| `REPORT-006` | **현재 설계** | AI recap 상태 | AI recap 상태는 PENDING/PROCESSING/COMPLETED/FAILED로 관리한다. | 테이블 정의서 6차 §11 |
| `REPORT-007` | **현재 설계** | AI recap 1회 시도 | V1에서는 리포트 생성 시 AI recap을 1회만 시도하며 실패 시 자동 재시도하지 않는다. | API 명세 상세2차 정책 요약 R18 |
| `REPORT-008` | **현재 설계** | AI recap 실패 조회 | AI recap이 PROCESSING 또는 FAILED여도 리포트 자원 자체가 존재하면 상세 GET은 200이다. | API 명세 상세2차 R191-R198 |
| `REPORT-009` | **현재 설계** | 사진 장면 분석 실패 | 사진 장면 분석 실패 또는 분석 가능한 사진이 없으면 리포트 전체 실패로 보지 않고 `photoScenes=[]`로 반환한다. | API 명세 상세2차 정책 요약 R19 |
| `REPORT-010` | **현재 설계** | 사진 분석 별도 상태 없음 | 사진 장면 분석에 별도의 status 필드를 두지 않는다. | API 명세 상세2차 R198 |
| `REPORT-011` | **현재 설계** | 월 평균 기분 | 월 평균 기분은 -50.0~50.0 범위의 `DECIMAL(3,1)`로 저장한다. | 테이블 정의서 6차 §12 |
| `REPORT-012` | **현재 설계** | 사진 장면 비율 | 장면별 ratio는 0~100 정수 퍼센트, count는 0 이상으로 관리한다. | 테이블 정의서 6차 §13 |
| `REPORT-013` | **현재 설계** | 대표 장소 기준 | 월간 리포트의 `top_place_id`는 개별 `places.place_id` 기준이며 대시보드 동 그룹 정책과 별개다. | 테이블 정의서 6차 §3,§11 |
| `REPORT-014` | **확정** | 대표 장소 동률 처리 | 월간 `top_place_id` 후보의 기록 횟수가 같으면 해당 후보 중 **가장 최근 기록이 존재하는 장소**를 선정한다. | 사용자 확정 |
| `REPORT-015` | **확정** | 대표 아티스트 동률 처리 | 월간 대표 아티스트 후보의 기록 횟수가 같으면 해당 후보 중 **가장 최근 기록이 존재하는 아티스트**를 선정한다. | 사용자 확정 |
| `REPORT-016` | **확정** | 사진 scene_tag 저장 방식 | `scene_tag`는 AI가 반환한 장면/분위기 문자열을 `VARCHAR`로 저장한다. 별도 ENUM/canonical 목록으로 제한하지 않는다. 현재 테이블의 문자열 컬럼 구조를 유지한다. | 사용자 확정 |

## AI 추천 플레이리스트

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `REC-001` | **현재 설계** | 현재 플레이리스트 수 | 사용자당 현재 추천 플레이리스트는 최대 1개만 유지한다. | 테이블 정의서 6차 §14 / API 명세 상세2차 R200-R204 |
| `REC-002` | **현재 설계** | 플레이리스트 미존재 | 현재 추천 플레이리스트가 없으면 오류가 아니라 200과 `playlist=null`을 반환한다. | API 명세 상세2차 R200,R204 |
| `REC-003` | **현재 설계** | 추천 처리 방식 | 새 추천 플레이리스트 생성은 동기 처리한다. | API 명세 상세2차 R205 |
| `REC-004` | **현재 설계** | 추천 컨텍스트 | 개인 기록, 주변 기록, 현재 위치/장소, 날씨, 기온, 시간을 AI 추천 컨텍스트로 사용한다. | API 명세 상세2차 정책 요약 R20 / R205-R212 |
| `REC-005` | **현재 설계** | 히스토리 최소값 없음 | 개인 기록과 주변 기록에는 최소 개수 제한이 없으며 0건이어도 추천할 수 있다. | API 명세 상세2차 R205,R212 |
| `REC-006` | **현재 설계** | 날씨·기온 필수 | 추천 플레이리스트 생성에서는 현재 위치의 날씨와 기온이 필수 컨텍스트이며 확보 실패 시 502 `WEATHER_API_ERROR`로 추천을 생성하지 않는다. | API 명세 상세2차 R210,R212 |
| `REC-007` | **확정** | AI 추천 장애 | AI 서비스 연동 실패 Error Code는 `AI_SERVICE_ERROR`로 통일한다. 기존 문서의 `AI_API_ERROR` 표기는 수정 대상이다. | 사용자 확정 |
| `REC-008` | **현재 설계** | 교체 원자성 | 새 추천 생성에 성공한 경우에만 기존 현재 플레이리스트를 교체하며 실패하면 기존 플레이리스트를 유지한다. | API 명세 상세2차 R205,R212 |
| `REC-009` | **현재 설계** | 컨텍스트 비저장 | 추천 생성 당시 날씨·기온·시간은 입력 컨텍스트로만 사용하고 playlist에 저장하거나 GET 응답으로 반환하지 않는다. | API 명세 상세2차 정책 요약 R20 / 테이블 정의서 §14 |
| `REC-010` | **현재 설계** | 추천 곡 순서 | 플레이리스트 음악 순번은 1 이상이며 같은 플레이리스트 내에서 순번이 중복되지 않는다. | 테이블 정의서 6차 §15 |
| `REC-011` | **V1 제외** | Spotify 플레이리스트 저장 | 추천 결과를 Spotify 계정에 저장하는 기능과 관련 API는 V1에서 제외한다. | API 명세 상세2차 정책 요약 R21 / R213 |

## 취향 투표

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `VOTE-001` | **현재 설계** | 장르별 하루 질문 | 전체 서비스 하루 1개가 아니라 `music_genre`별 하루 1개의 질문을 제공한다. | API 명세 상세2차 정책 요약 R22 / 테이블 정의서 §16 |
| `VOTE-002` | **현재 설계** | 오늘 질문 대상 | 사용자는 자신의 `users.music_genre`와 같은 장르의 오늘 질문 1건을 조회한다. | API 명세 상세2차 R221,R226 |
| `VOTE-003` | **현재 설계** | 미응답/응답 표시 | 오늘 질문 조회의 `myAnswer`는 미응답이면 NULL, 응답했으면 실제 선택지 텍스트이다. | API 명세 상세2차 R221-R226 |
| `VOTE-004` | **현재 설계** | 선택 입력 | 답변 API는 `selectedOption`으로 `A` 또는 `B`만 받는다. | API 명세 상세2차 R227-R236 |
| `VOTE-005` | **현재 설계** | 선택 저장 | 서버는 A/B를 질문의 실제 선택지 텍스트로 매핑하고 DB `vote_answers.selected_option`에는 실제 텍스트를 저장한다. | API 명세 상세2차 R227 / 테이블 정의서 §17 |
| `VOTE-006` | **현재 설계** | 첫 답변 불변 | 사용자당 같은 질문에 첫 답변만 허용하며 이후 수정할 수 없다. | API 명세 상세2차 R227,R233,R236 / 테이블 정의서 §17 |
| `VOTE-007` | **현재 설계** | 활성 질문 조건 | 현재 날짜이면서 현재 사용자의 대표 장르와 같은 질문에만 답변할 수 있다. | API 명세 상세2차 R227,R234,R236 |
| `VOTE-008` | **현재 설계** | 답변 결과 | 답변 성공 시 실제 선택지 텍스트와 현재 A/B 결과 비율을 반환한다. | API 명세 상세2차 R227 |

## 매칭

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `MATCH-001` | **확정** | 매칭 구성 | 동일 `vote_question_id`에서 A 선택 사용자 2명 + B 선택 사용자 2명이 모이면 4인 채팅방을 생성한다. | API 명세 상세2차 정책 요약 R23 / 테이블 정의서 §18-19 |
| `MATCH-002` | **확정** | 매칭 운영 종료 | 당일 23:00 이후에는 신규 매칭 요청을 생성하지 않는다. | 사용자 확정 / API 명세 상세2차 R238,R246 |
| `MATCH-003` | **확정** | 23시 대기 요청 만료 | 당일 23:00 시점에 남아 있는 모든 WAITING 요청을 EXPIRED로 변경하고 이후 추가 매칭하지 않는다. | 사용자 확정 / API 명세 상세2차 정책 요약 R23 |
| `MATCH-004` | **현재 설계** | 매칭 요청 기준 답변 | 매칭 요청은 현재 사용자가 소유한 오늘 날짜의 유효한 투표 답변을 기준으로 한다. | API 명세 상세2차 R238-R246 |
| `MATCH-005` | **확정** | 잘못된 voteAnswerId 통합 | voteAnswerId가 미존재·타인 소유·과거 날짜이면 모두 404 `VOTE_ANSWER_NOT_FOUND`로 처리한다. | API 명세 상세2차 R242,R246 |
| `MATCH-006` | **현재 설계** | 답변당 요청 1회 | 하나의 `vote_answer`에는 하나의 `match_request`만 생성할 수 있다. | 테이블 정의서 §18 / API 명세 상세2차 R243,R246 |
| `MATCH-007` | **현재 설계** | 요청 초기 상태 | 매칭 요청 생성 직후 상태는 WAITING이다. | API 명세 상세2차 R238,R246 |
| `MATCH-008` | **현재 설계** | 상태 집합 | 매칭 상태는 WAITING/MATCHED/EXPIRED 세 가지다. | 테이블 정의서 §18 / API 명세 상세2차 R247 |
| `MATCH-009` | **현재 설계** | 매칭 상태 조회 | 프론트는 polling으로 상태를 조회하며 MATCHED이면 `chatRoomId`, WAITING/EXPIRED이면 NULL을 반환한다. | API 명세 상세2차 R247-R253 |
| `MATCH-010` | **현재 설계** | 상태 조회 소유권 | 매칭 요청 소유자만 상태를 조회할 수 있고 미존재/타인 소유는 동일한 404다. | API 명세 상세2차 R247,R251,R253 |
| `MATCH-011` | **현재 설계** | 채팅방 삭제 연계 | 채팅방 삭제 시 해당 방을 참조하는 MATCHED `match_requests`는 FK 정책상 CASCADE 삭제된다. | 테이블 정의서 6차 §18 |
| `MATCH-012` | **확정** | 매칭 후보 FIFO | 동일 질문에서 매칭 가능한 WAITING 사용자가 필요한 인원보다 많으면 먼저 생성된 매칭 요청부터 선택하는 FIFO 방식을 사용한다. 구현 정렬 기준은 `created_at ASC`, 동일 시각이면 `match_request_id ASC`로 결정성을 확보한다. | 사용자 확정 |

## 채팅

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `CHAT-001` | **현재 설계** | 채팅방 수명 | 채팅방은 생성 당일에만 유지하며 자정 또는 참여자 4명 모두 퇴장 시 물리 삭제한다. | 테이블 정의서 6차 §19-20 / API 명세 상세2차 R279-R285 |
| `CHAT-002` | **확정** | 방 단일 선택지 제거 | A2+B2 방이므로 `chat_rooms.selected_option`을 두지 않고 각 참여자의 선택은 `vote_answers.selected_option`에서 판별한다. | 테이블 정의서 6차 §19 / 최근 정책 동기화 |
| `CHAT-003` | **현재 설계** | 참가자 선등록 | 매칭 완료 시 4명의 `chat_room_participants`를 먼저 생성한다. | 테이블 정의서 6차 §20 |
| `CHAT-004` | **현재 설계** | 최초 입장 | 실제 최초 채팅방 입장 시 `joined_at`을 기록한다. | API 명세 상세2차 R256-R262 |
| `CHAT-005` | **현재 설계** | 재입장 금지 | `left_at`이 존재하는 사용자는 해당 채팅방에 다시 입장할 수 없고 403 `CHAT_REENTRY_FORBIDDEN`이다. | API 명세 상세2차 R259,R262 |
| `CHAT-006` | **현재 설계** | 참가자 접근 은닉 | 삭제된 방 또는 비참가자의 접근은 동일한 404 `CHAT_ROOM_NOT_FOUND`로 처리한다. | API 명세 상세2차 R262,R268,R278,R285 |
| `CHAT-007` | **현재 설계** | 채팅방 정보 노출 | 채팅방 정보 조회는 참가자에게 참여자의 `userId`, `nickname`을 반환한다. | API 명세 상세2차 R263-R268 |
| `CHAT-008` | **현재 설계** | 메시지 발신 권한 | `joined_at`이 존재하고 `left_at`이 NULL인 현재 참가자만 메시지를 보낼 수 있다. | 테이블 정의서 6차 §21 |
| `CHAT-009` | **현재 설계** | 메시지 길이 | 텍스트 채팅 메시지 최대 길이는 500자이다. | 테이블 정의서 6차 §21 |
| `CHAT-010` | **확정** | 메시지 내역 페이지 | 메시지 내역은 Cursor 기반, 기본 30·최대 100이며 API 반환 순서는 최신→과거다. 전체 목록 기본값 20 정책의 명시적 예외다. | 사용자 확정 2026-09-21 / API 명세 상세2차 R269-R278 |
| `CHAT-011` | **현재 설계** | 방 삭제와 메시지 | 채팅방 물리 삭제 시 참여자와 메시지는 CASCADE 삭제되며 메시지 이력도 조회할 수 없다. | 테이블 정의서 6차 §20-21 / API 명세 상세2차 R278 |
| `CHAT-012` | **현재 설계** | 퇴장 처리 | 퇴장 시 `left_at`을 기록하고 남은 참가자에게 퇴장 사실을 전달하며 이미 퇴장한 사용자의 반복 퇴장은 409다. | API 명세 상세2차 R279-R285 |
| `CHAT-013` | **보류** | WebSocket 상세 | WebSocket Access Token 전달 방식과 실시간 메시지 상세 명세는 후속 설계로 보류한다. | API 명세 상세2차 정책 요약 R28 |

## 알림

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `NOTI-001` | **현재 설계** | 알림 유형 | 알림 유형은 MONTHLY_REPORT_CREATED, AI_PLAYLIST, MATCH_COMPLETED, FRIEND_REQUEST, FRIEND_ACCEPTED의 5종이다. | 테이블 정의서 6차 §22 |
| `NOTI-002` | **현재 설계** | 알림 목록 | 현재 사용자 알림을 최신순 Cursor 기반으로 조회하며 기본 20, 최대 100이다. | API 명세 상세2차 R287-R294 |
| `NOTI-003` | **현재 설계** | 알림 GET read-only | GET `/api/notifications` 자체는 `read_at`을 변경하지 않는다. | API 명세 상세2차 R287,R294 |
| `NOTI-004` | **현재 설계** | 읽음 일괄 처리 | 개별 읽음 API 없이 `notificationIds` 배열을 받는 batch read API만 제공한다. | API 명세 상세2차 R295-R300 |
| `NOTI-005` | **현재 설계** | batch read 원자성 | 배열 중 하나라도 미존재 또는 타인 소유 알림이면 전체 요청을 404로 실패시키고 일부만 읽음 처리하지 않는다. | API 명세 상세2차 R300 |
| `NOTI-006` | **현재 설계** | 자동 읽음 UI 흐름 | 프론트는 알림 페이지를 받은 뒤 해당 페이지의 unread IDs를 모아 batch read API를 별도 호출한다. | API 명세 상세2차 R287,R294 |
| `NOTI-007` | **현재 설계** | Target 삭제 후 알림 유지 | 연결 Target이 삭제되어도 알림은 과거 이력으로 유지하고 Target FK만 NULL로 만든다. | API 명세 상세2차 정책 요약 R32 / 테이블 정의서 §22 |
| `NOTI-008` | **현재 설계** | Target 없는 알림 UI | `targetId=null`인 과거 알림은 표시하되 해당 Target으로 이동/처리하는 기능은 비활성화한다. | API 명세 상세2차 정책 요약 R32 |
| `NOTI-009` | **현재 설계** | 알림 삭제 범위 | 알림 삭제는 알림 자체만 삭제하고 연결된 Target 리소스는 삭제하지 않는다. | API 명세 상세2차 R301-R306 |
| `NOTI-010` | **현재 설계** | 알림 소유권 은닉 | 읽음/삭제 시 미존재 또는 타인 소유 알림은 404 `NOTIFICATION_NOT_FOUND`로 처리한다. | API 명세 상세2차 R298,R304,R306 |


## 목록·페이지네이션

| 정책 ID | 상태 | 정책명 | 현재 규칙 | 근거 |
|---|---|---|---|---|
| `LIST-001` | **확정** | 기본 페이지 크기 | 커서 기반 목록 API의 `size` 기본값은 20이며, 채팅 메시지 내역만 기본 30을 유지한다. | 사용자 확정 2026-09-21 / 팀원 OpenAPI v2.4 |
| `LIST-002` | **확정** | 최대 페이지 크기 | 모든 커서 기반 목록 API의 `size` 범위는 최소 1, 최대 100으로 통일한다. | 사용자 확정 2026-09-21 / OpenAPI v2.6 |
| `LIST-003` | **현재 설계** | 빈 목록 | 정상 조회 대상이 없을 경우 목록 API는 200과 빈 배열을 반환하는 방향으로 설계되어 있다. | API 명세 상세2차 places/friends/report/chat/notification |

## 보류·V1 제외

| 항목 | 상태 | V1 처리 |
|---|---|---|
| Presigned URL 업로드 | **보류** | V1은 백엔드 수신 후 GCS 업로드 유지 |
| `record_drafts` | **V1 제외·실제 테이블 존재** | 기능 범위에서는 제외됐지만 Flyway V1에 테이블이 존재한다. 삭제하려면 별도 승인된 신규 migration이 필요하다. |
| AI 기억 검색 | **V1 제외** | 기존 API 설계 내용은 문서에 보존하고 `[V1 제외]`로 표시한다. V1 구현 대상에서는 제외한다. |
| Spotify 계정 저장 | **V1 제외** | 추천 결과를 Spotify 계정에 저장하는 API를 제공하지 않음 |

## 이번 버전에서 해소된 사항

- 기존 `OQ-025`는 해소되었다. Kakao Maps SDK 호출이 정상 성공했으나 법정동(`region_type=B`) 결과가 없는 경우 오류로 처리하지 않고 `legal_dong_code=NULL`, `legal_dong_name=NULL`로 자물쇠 생성을 허용한다.
- Kakao Maps SDK 호출 자체 실패는 법정동 없음과 구분하며, 프론트에서 요청 전 재시도 또는 생성 중단 처리한다.

## 미확정 / 추가 결정 필요

| ID | 미정 사항 | 영향 문서 |
|---|---|---|
| `WTH-002` | 국내 기상청 격자 범위 밖 좌표의 HTTP 상태와 오류 코드 | 정책, API |
| `OQ-001` | 음악 장르의 정식 목록 | 정책, 테이블 |
| `OQ-021` | 일반 AI 플레이리스트 곡 수 | 정책, API |
| `OQ-022` | 알림 보관 기간 | 정책, 테이블 |
| `OQ-023` | 월간 리포트 배치 실행 시각 | 정책 |

미정 항목은 문서에 추론으로 채우지 않는다.

## 설계 문서 동기화 필요사항

> 아래 항목은 이 정책 정의서를 기준으로 기존 API/테이블 정의서/ERD/SQL에 역반영해야 한다.

| ID | 대상 | 수정 필요 내용 |
|---|---|---|
| `SYNC-001` | `places.legal_dong_code/name` | `legal_dong_code`, `legal_dong_name`을 NULL 허용으로 변경하고 두 값이 함께 존재하거나 함께 NULL이 되도록 한다. 법정동 미확인 시 자물쇠 생성은 허용한다. |
| `SYNC-002` | `chat_rooms.selected_option` | A2+B2 정책에 따라 `chat_rooms.selected_option`을 제거한다. 선택 정보는 `vote_answers.selected_option`으로 판별한다. |
| `SYNC-003` | `record_drafts` | 정책상 V1 제외지만 Flyway V1에 실제 테이블이 존재한다. 기능에서 사용하지 않으며 삭제 여부는 별도 migration 결정으로 남긴다. |
| `SYNC-004` | 취향 투표 API | `music_genre=NULL` 사용자의 오늘 질문 조회/투표 제출을 `403 MUSIC_GENRE_REQUIRED`로 통일한다. |
| `SYNC-005` | AI 오류 코드 | 기존 `AI_API_ERROR` 표기는 `AI_SERVICE_ERROR`로 통일한다. 최소 기억 검색 API와 AI 추천 API 문구를 함께 점검한다. |
| `SYNC-007` | 매칭 정책 | 매칭 후보 선택은 FIFO(`created_at ASC`, 동률 `match_request_id ASC`)로 명시한다. |
| `SYNC-008` | 서비스 시간 | 오늘/23:00/자정/월말 판단 기준을 `Asia/Seoul`로 명시한다. |
| `SYNC-009` | 회원 탈퇴/사진 | 사용자 soft delete 후 연결 서비스 데이터와 최종 GCS 사진을 보존하는 정책을 API/테이블 정의서에 반영한다. |
| `SYNC-010` | 월간 리포트 | 대표 장소/대표 아티스트 동률 시 가장 최근 기록이 존재하는 후보를 선택하도록 명시한다. |
| `SYNC-011` | `monthly_photo_scene_stats.scene_tag` | AI 반환 문자열을 `VARCHAR`로 저장하고 별도 ENUM/canonical 제한을 두지 않는 정책을 명시한다. |
| `SYNC-012` | 인증 | 로그아웃 시 클라이언트 Access Token 즉시 폐기, Refresh Token revoke/Cookie 만료, V1 다중 기기 로그인 미지원, Refresh Token SHA-256 저장, 닉네임 확정 형식, Cookie/CORS 역할 분리를 AUTH/API 문서에 명시한다. |

## API 전수 대조 체크리스트

> 아래 엔드포인트는 최신 API 명세 상세2차의 실제 행을 기준으로 추출했다. 최종 정책 정의서 작성 시 각 엔드포인트가 최소 1개 이상의 정책 항목과 연결되는지 다시 검사한다.

| Domain | API 명세 상세2차 행 | 기능 | Method | URL |
|---|---:|---|---|---|
| SECURITY | 현재 구현 | CSRF Cookie 발급 | `GET` | `/api/csrf` |
| AUTH | 4 | 로그인 | `POST` | `/api/auth/login` |
| USER | 10 | 회원가입 | `POST` | `/api/users/signup` |
| AUTH | 16 | 로그아웃 | `POST` | `/api/auth/logout` |
| AUTH | 19 | Access Token 재발급 | `POST` | `/api/auth/refresh` |
| USER | 24 | 마이페이지 내 정보 조회 | `GET` | `/api/users/me` |
| USER | 28 | 닉네임 변경 | `PATCH` | `/api/users/me/nickname` |
| USER | 36 | 비밀번호 변경 | `PATCH` | `/api/users/me/password` |
| USER | 44 | 회원 탈퇴 | `DELETE` | `/api/users/me` |
| HOME LOCK (RECORD) | 51 | 인기 장소 마커 조회 | `GET` | `/api/places/popular` |
| HOME LOCK (RECORD) | 64 | 홈애서 내 자물쇠 목록 | `GET` | `/api/users/me/places` |
| MUSIC | 82 | 음악 검색 | `GET` | `/api/music/search` |
| UPLOAD | 90 | 사진 임시 업로드 | `POST` | `/api/uploads` |
| RECOMMEND | 98 | 사진 기반 음악 추천 | `POST` | `/api/recommendations/photo` |
| LOCK (RECORD) | 106 | 자물쇠 생성 | `POST` | `/api/records` |
| LOCK (RECORD) | 115 | 자물쇠 상세 조회 | `GET` | `/api/records/{recordId}` |
| LOCK (RECORD) | 121 | 자물쇠 코멘트 수정 | `PATCH` | `/api/records/{recordId}/comment` |
| LOCK (RECORD) | 129 | 자물쇠 삭제 | `DELETE` | `/api/records/{recordId}` |
| FRIEND | 136 | 친구 목록 | `GET` | `/api/users/me/friends` |
| FRIEND | 144 | 받은 친구 요청 목록 | `GET` | `/api/users/me/friend-requests/received` |
| FRIEND | 152 | 보낸 친구 요청 목록 | `GET` | `/api/users/me/friend-requests/sent` |
| FRIEND | 160 | 친구 요청 | `POST` | `/api/users/me/friend-requests` |
| FRIEND | 170 | 친구 요청 수락 | `POST` | `/api/friend-requests/{friendRequestId}/accept` |
| FRIEND | 175 | 친구 요청 거절/취소 | `DELETE` | `/api/friend-requests/{friendRequestId}` |
| FRIEND | 180 | 친구 삭제 | `DELETE` | `/api/friendships/{friendshipId}` |
| REPORT | 186 | 월간 리포트 목록 | `GET` | `/api/monthly-reports` |
| REPORT | 191 | 월간 리포트 상세 | `GET` | `/api/monthly-reports/{monthlyReportId}` |
| RECOMMEND | 200 | 현재 추천 플레이리스트 조회 | `GET` | `/api/recommendations/playlists` |
| RECOMMEND | 205 | 새 추천 플레이리스트 생성 | `POST` | `/api/recommendations/playlists` |
| VOTE | 221 | 오늘의 투표 조회 | `GET` | `/api/vote-questions/today` |
| VOTE | 227 | 투표 답변 | `POST` | `/api/vote-questions/{voteQuestionId}/answers` |
| MATCH | 238 | 매칭 요청 생성 | `POST` | `/api/match-requests` |
| MATCH | 247 | 매칭 상태 조회 | `GET` | `/api/match-requests/{matchRequestId}` |
| CHAT | 256 | 채팅방 입장 | `POST` | `/api/chat-rooms/{chatRoomId}/members` |
| CHAT | 263 | 채팅방 정보 조회 | `GET` | `/api/chat-rooms/{chatRoomId}` |
| CHAT | 269 | 채팅 메시지 내역 조회 | `GET` | `/api/chat-rooms/{chatRoomId}/messages` |
| CHAT | 279 | 채팅방 퇴장 | `POST` | `/api/chat-rooms/{chatRoomId}/leave` |
| NOTIFICATION | 287 | 알림 목록 | `GET` | `/api/notifications` |
| NOTIFICATION | 295 | 알림 일괄 읽음 처리 | `PATCH` | `/api/notifications/read` |
| NOTIFICATION | 301 | 알림 삭제 | `DELETE` | `/api/notifications/{notificationId}` |
| MEMORY | 308 | 기억 검색 | `POST` | `/api/memories/search` |
| WEATHER | OpenAPI v2.6 | 날씨 조회 | `GET` | `/api/weather` |
| HOME LOCK (RECORD) | 팀원 OpenAPI v2.4 | 클러스터·장소 인기 음악 조회 | `POST` | `/api/places/popular-tracks/search` |
| HOME LOCK (RECORD) | 팀원 OpenAPI v2.4 | 클러스터·장소 내 자물쇠 목록 | `POST` | `/api/users/me/records/search` |
| LOCK (RECORD) | OpenAPI v2.6 | 내 자물쇠 법정동 그룹 | `GET` | `/api/records/regions` |

## 테이블 전수 대조 체크리스트

> 기존 1~22번 테이블 순서는 유지한다. `record_drafts`는 기능상 `[V1 제외]`지만 Flyway V1에 실제 존재한다. V2 레거시 `weather`와 V3 날씨 캐시 테이블을 23~25번으로 추가한다.

- [x] 1. `users`
- [x] 2. `refresh_tokens - 로그인 유지 토큰 테이블`
- [x] 3. `places - 장소 테이블`
- [x] 4. `music_tracks - 음악 정보 테이블`
- [x] 5. `records - 자물쇠(음악기록핀) 테이블`
- [x] 6. `record_photos - 자물쇠 사진`
- [x] 7. `uploads - 임시 이미지 업로드 테이블`
- [x] 8. `record_drafts - 자물쇠 임시저장`
- [x] 9. `friend_requests - 친구 요청 테이블`
- [x] 10. `friendships - 친구 관계 테이블`
- [x] 11. `monthly_reports - 월간 리포트 테이블`
- [x] 12. `monthly_mood_stats - 월간 기분 통계 테이블`
- [x] 13. `monthly_photo_scene_stats - 월간 사진 분위기 통계 테이블`
- [x] 14. `recommendation_playlists - 추천 플레이리스트 테이블`
- [x] 15. `recommendation_playlist_items - 추천 플레이리스트 음악 테이블`
- [x] 16. `vote_questions - 취향 투표 질문 테이블`
- [x] 17. `vote_answers - 취향 투표 답변 테이블`
- [x] 18. `match_requests - 매칭 요청 테이블`
- [x] 19. `chat_rooms - 채팅방 테이블`
- [x] 20. `chat_room_participants - 채팅방 참여자 테이블`
- [x] 21. `chat_messages - 채팅 메시지 테이블`
- [x] 22. `notifications - 알림 테이블`
- [x] 23. `weather - 도시 단위 레거시 날씨 테이블`
- [x] 24. `weather_grids - 기상청 격자 테이블`
- [x] 25. `weather_grid_forecasts - 격자별 날씨 예보 캐시 테이블`

## 정책 변경 관리 원칙

1. 새 정책을 확정하면 이 문서를 먼저 수정한다.
2. 같은 변경을 API 설계서, 테이블 정의서, ERD/SQL에 동기화한다.
3. `미확정 / 추가 결정 필요` 항목은 구현자가 임의로 결정하지 않는다.
4. 보류 또는 V1 제외 정책은 구현 범위에 포함하지 않는다.
5. 기존 문서와 본 정책이 충돌하면 가장 최근 사용자 확정 정책을 우선한다.

## 문서 사용 메모

- 이 문서는 MULO V1의 정책 SSOT로 사용한다.
- 최신 자료에서 근거가 없는 사항은 자동으로 확정하지 않았다.
- `미확정 / 추가 결정 필요`와 `보류` 항목은 구현 단계에서 임의 확정하지 않는다.
- DB 컬럼 사전이 아니라 서비스 동작 판단을 위한 정책 문서이므로, 세부 타입·인덱스·제약조건은 테이블 정의서/ERD/SQL에서 관리하되 정책과 충돌해서는 안 된다.
- API 전수 대조는 OpenAPI v2.7의 42개 경로·46개 작업을 기준으로 한다. 장소 클러스터 조회 2개는 담당 팀원의 OpenAPI v2.4 계약을 우선하며, 현재 구현의 `GET /api/csrf`를 포함한다. 테이블 정의서는 Flyway V1~V4의 실제 25개 테이블을 기준으로 한다. `record_drafts`와 레거시 `weather`는 실제 존재와 정책 상태를 함께 표시한다.
