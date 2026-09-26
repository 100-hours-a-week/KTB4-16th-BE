# MULO 테이블 정의서 v8.1

> 작성일: 2026-09-21 (KST)  
> 실제 스키마 기준: `app/mulo-be` 현재 `feature` 작업 트리의 Flyway V1~V4  
> 설계·정책 기준: 테이블 정의서 v7.6, 서비스 정책 정의서 v1.9, OpenAPI v2.6, 현재 구현 문서  
> 원칙: Flyway에 존재하는 구조와 목표 정책이 다르면 둘을 분리해 기록하고 미확정 사항은 `Open Question`으로 유지한다.

### 문서 상태 표기

| 상태 | 의미 |
| --- | --- |
| `구현됨` | Flyway V1~V4에 실제 적용되는 구조 |
| `구현됨·정책 확인 필요` | 실제 구조는 존재하지만 사용 또는 유지 정책이 확정되지 않음 |
| `V1 제외·실제 테이블 존재` | V1 기능 범위에서는 제외됐지만 현재 Flyway에는 테이블이 존재함 |
| `설계 규칙` | DB 컬럼 외에 서비스가 지켜야 하는 규칙 |

### v8.1 핵심 변경 사항

- Flyway V1~V4를 순서대로 적용한 실제 테이블 25개를 기준으로 재정리
- V2의 레거시 `weather`를 실제 테이블로 추가하고 격자 캐시 테이블과 역할을 분리
- V4의 `places` 컬럼 변경, 법정동 쌍 CHECK, 좌표 UNIQUE를 실제 구조로 반영
- `record_drafts`는 정책상 V1 제외이지만 Flyway V1에 존재함을 명시
- 현재 작업 트리에 구현된 월간 리포트 엔티티·집계·목록 조회 상태를 반영
- 설계 문서와 Flyway 사이의 차이는 문서 끝의 정합성 표에서 원본과 현재 상태를 함께 표시

### v7.6에서 승계한 핵심 변경 사항

- `places.legal_dong_code`, `places.legal_dong_name`은 NULL 허용: Kakao Maps SDK의 법정동(`region_type=B`) 결과가 있으면 각각 `code`, `region_3depth_name`을 저장하고, 정상 조회 결과 법정동이 없으면 둘 다 NULL로 저장
- `legal_dong_code`와 `legal_dong_name`은 둘 다 존재하거나 둘 다 NULL이어야 하도록 CHECK 제약을 둔다
- 자물쇠 생성 위치는 프론트가 브라우저 Geolocation API로 최초 좌표를 얻고 Kakao Maps SDK에서 사용자가 수정한 최종 좌표를 확정한다
- 프론트는 최종 좌표에 대해 Kakao Maps SDK `services.Geocoder.coord2RegionCode()`를 호출해 법정동 코드/명을 얻고 `latitude`, `longitude`, `legalDongCode`, `legalDongName`을 백엔드에 전달한다
- 대시보드 지역 그룹 키는 `legal_dong_code`이며 `legal_dong_name`은 표시용이다. `legal_dong_code IS NULL`인 데이터는 하나의 미확인 그룹으로 집계하고 화면에는 `확인할 수 없음`으로 표시한다
- 홈의 인기 자물쇠 지도는 조회 시점 기준 최근 7일 이내 생성된 삭제되지 않은 자물쇠만 대상으로 한다
- 인기 자물쇠 마커 클릭 후 음악 랭킹도 동일한 최근 7일 자물쇠만 집계하며 음악별 `count DESC`, 동률은 해당 음악의 가장 최근 Record 생성 시각 내림차순으로 정렬한다
- 내 자물쇠 보기는 7일 제한을 적용하지 않고 현재 사용자의 삭제되지 않은 모든 자물쇠를 대상으로 한다
- 지도 마커의 개별 장소와 클러스터는 담당 팀원 계약에 따라 `POST /api/places/popular-tracks/search`, `POST /api/users/me/records/search` Body의 `placeIds`로 함께 처리한다. ID 1개는 개별 장소, 여러 개는 클러스터를 의미하며 기존 단일 장소 GET 계약은 사용하지 않는다
- 인기 지도 마커에는 최근 7일 기준 `recordsCount`, 내 자물쇠 지도 마커에는 전체 기간 기준 `myRecordsCount`를 제공한다
- 사용자 자물쇠 대시보드는 별도 dashboard 테이블을 만들지 않고 `records`를 `places.legal_dong_code` 기준으로 그룹 조회한다. 폴더 표시명은 `legal_dong_name`이다
- 커서 기반 목록 API의 `size`는 기본 20, 최대 100이며 채팅 메시지 내역만 기본 30을 유지한다
- Kakao SDK 호출 자체가 실패한 경우에는 NULL 법정동으로 간주하지 않고 프론트에서 생성 요청을 중단하거나 재시도한다. 기존 OQ-025는 이 정책으로 해소된다
- 레거시 `weather`를 23번으로 명시하고 `weather_grids`, `weather_grid_forecasts`를 24~25번으로 배치
- 날씨 캐시는 `(weather_grid_id, forecast_at)` 유일성 보장
- `records.weather_condition`은 7개 상태 `CLEAR/CLOUDY/OVERCAST/RAIN/SNOW/RAIN_SNOW/SHOWER`만 저장
- `record_drafts`는 기존 설계 내용을 보존하고 `[V1 제외]`로 표시
- AI 기억 검색 및 Spotify 계정 저장 기능은 V1 범위에서 제외
- `chat_rooms.selected_option`은 두지 않음(A 2명 + B 2명 구성)
- 기존 사진/업로드/친구/리포트/투표/매칭/채팅/알림 구조는 동기화 설계서와 충돌하지 않는 범위에서 유지

---

## 1. users

#### 1. 테이블 설명

사용자의 계정 및 기본 정보를 저장하는 테이블이다. 회원가입, 로그인, 마이페이지 등의 회원 기능에서 사용하며, 사용자와 관련된 서비스 데이터를 식별하는 기준이 된다. 또한 사용자의 음악 기록을 AI가 분석해 결정한 대표 음악 장르 1개를 저장하여 장르별 취향 투표 질문 제공에 활용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | NOT NULL | `AUTO_INCREMENT` | PK | 사용자 고유 ID | 사용자를 식별하는 숫자형 ID이며 향후 데이터 증가 가능성을 고려하여 `BIGINT`를 사용한다. | 사용자 식별 및 다른 테이블에서 사용자를 참조하는 기준값으로 사용한다. |
| `email` | `VARCHAR(254)` | NOT NULL | - | - | 로그인에 사용하는 이메일 | 이메일은 가변 길이 문자열이며 서비스에서 허용하는 이메일 최대 길이를 고려하여 254자로 설정한다. 탈퇴 후에도 기존 계정 정보를 보존해야 하므로 원본 이메일 값은 유지한다. | 로그인 계정 식별 및 사용자 이메일 정보 보존에 사용한다. |
| `active_email` | `VARCHAR(254)` | NULL | - | UK | 활성 회원 이메일 | `deleted_at IS NULL`이면 `email` 값을 가지고, 탈퇴한 회원이면 `NULL`이 되도록 생성되는 Generated Column이다. | 활성 회원끼리만 이메일 UNIQUE 제약을 적용하여 탈퇴한 회원의 이메일을 재사용할 수 있도록 한다. |
| `password_hash` | `VARCHAR(60)` | NOT NULL | - | - | 해시 처리된 비밀번호 | 비밀번호 원문을 저장하지 않고 bcrypt 해시값을 저장한다. bcrypt 인코딩 결과 문자열은 60자로 고정되므로 `VARCHAR(60)`을 사용한다. | 이메일 로그인 시 입력된 비밀번호와 저장된 해시값을 비교하여 사용자 인증에 사용한다. |
| `nickname` | `VARCHAR(10)` | NOT NULL | - | - | 서비스에서 사용하는 사용자 닉네임 | 닉네임 정책인 2~10자를 반영한다. 탈퇴 후에도 기존 사용자 데이터를 보존하기 위해 실제 닉네임 값은 유지한다. | 프로필, 친구 목록, 채팅 등에서 사용자 표시 이름으로 사용한다. |
| `active_nickname` | `VARCHAR(10)` | NULL | - | UK | 활성 회원 닉네임 | `deleted_at IS NULL`이면 `nickname` 값을 가지고, 탈퇴한 회원이면 `NULL`이 되도록 생성되는 Generated Column이다. | 활성 회원끼리만 닉네임 UNIQUE 제약을 적용하여 탈퇴한 회원의 닉네임을 재사용할 수 있도록 한다. |
| `music_genre` | `VARCHAR(50)` | NULL | `NULL` | - | 사용자의 대표 음악 장르 | 사용자가 직접 선택하는 값이 아니라 사용자의 음악 기록을 AI가 분석하여 결정한 대표 장르 1개를 저장한다. 아직 대표 장르가 결정되지 않은 사용자가 존재할 수 있으므로 `NULL`을 허용한다. | 사용자의 대표 장르와 동일한 `vote_questions.music_genre`의 질문을 조회하여 장르별 취향 투표를 제공하는 데 사용한다. |
| `created_at` | `DATETIME` | NOT NULL | `CURRENT_TIMESTAMP` | - | 계정 생성 시각 | 계정이 생성된 날짜와 시각을 기록하기 위해 `DATETIME`을 사용한다. | 회원가입 시점을 기록한다. |
| `updated_at` | `DATETIME` | NULL | `NULL` | - | 회원 정보 최종 수정 시각 | 닉네임 등 사용자 정보가 수정된 마지막 시점을 기록한다. 한 번도 수정되지 않은 경우 `NULL`을 허용한다. | 회원 정보 변경 시점을 추적한다. |
| `deleted_at` | `DATETIME` | NULL | `NULL` | - | 계정 탈퇴 시각 | 활성 회원은 `NULL`이며, 탈퇴 시 탈퇴 시각을 기록한다. 해당 값에 따라 `active_email`,`active_nickname` 생성 컬럼 값이 자동으로 결정된다. | 소프트 삭제 여부를 판단하고 탈퇴 회원의 이메일·닉네임 재사용 여부를 제어한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_users` | PRIMARY KEY | `user_id` | - | 각 사용자를 고유하게 식별한다. |
| `uk_users_active_email` | UNIQUE | `active_email` | - | 탈퇴하지 않은 활성 회원 사이에서 이메일 중복을 방지한다. 탈퇴 회원의 `active_email`은 `NULL`이므로 동일 이메일을 새로운 회원이 재사용할 수 있다. |
| `uk_users_active_nickname` | UNIQUE | `active_nickname` | - | 탈퇴하지 않은 활성 회원 사이에서 닉네임 중복을 방지한다. 탈퇴 회원의 `active_nickname`은 `NULL`이므로 동일 닉네임을 새로운 회원이 재사용할 수 있다. |
| `chk_users_nickname_length` | CHECK | `nickname` | - | 닉네임 길이를 서비스 정책인 2~10자로 제한한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_user_music_genre` | 사용자의 음악 기록을 AI가 분석하여 대표 음악 장르 하나를 `music_genre`에 저장한다. 대표 장르가 아직 결정되지 않은 경우 `NULL`을 허용한다. |
| `rule_user_genre_vote_question` | 취향 투표 제공 시 사용자의 `music_genre`와 동일한 장르의 당일 `vote_questions`를 조회한다. `users.music_genre`와 `vote_questions.music_genre`는 값 기준으로 매칭하며 FK 관계를 두지 않는다. |
| `rule_user_genre_vote_eligibility` | `music_genre`가 결정되지 않은 사용자는 취향 투표에 참여할 수 없다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `records` | `users.user_id` ↔ `records.user_id` | 1:N | 한 사용자는 여러 자물쇠를 생성할 수 있으며, 각 자물쇠는 작성자 한 명에게 속한다. |
| `friendships` | `users.user_id` ↔ `friendships.user_low_id`, `user_high_id` | 1:N | 한 사용자는 여러 친구 관계에 참여할 수 있으며, 두 사용자 ID는 `user_low_id`, `user_high_id`로 정규화하여 `friendships` 테이블에서 방향 없는 친구 관계로 관리한다. |
| `friend_requests` | `users.user_id`↔`friend_requests.requester_id` , `friend_requests.addressee_id` | 1:N | `friend_requests.requester_id` 한 사용자는 여러 친구 요청을 보낼 수 있으며, 각 친구 요청은 한 명의 요청자를 가진다. 
`friend_requests.addressee_id` 한 사용자는 여러 친구 요청을 받을 수 있으며, 각 친구 요청은 한 명의 수신자를 가진다. |
| `monthly_reports` | `users.user_id` ↔ `monthly_reports.user_id` | 1:N | 한 사용자는 월별로 여러 리포트를 가질 수 있으며, 각 월간 리포트는 한 사용자의 기록을 기준으로 생성된다. |
| `vote_answers` | `users.user_id` ↔ `vote_answers.user_id` | 1:N | 한 사용자는 여러 취향 투표에 참여할 수 있으며 각 투표 답변의 작성자를 식별한다. |
| `match_requests` | `users.user_id` ↔ `match_requests.user_id` | 1:N | 한 사용자는 서로 다른 날짜에 여러 매칭 요청을 생성할 수 있으며, 각 매칭 요청은 한 사용자에게 속한다. |
| `chat_room_participants` | `users.user_id` ↔ `chat_room_participants.user_id` | 1:N | 사용자가 참여한 채팅방과 참여 상태를 관리하기 위해 연결한다. |
| `chat_messages` | `users.user_id` ↔ `chat_messages.user_id` | 1:N | 한 사용자는 채팅방에서 여러 메시지를 전송할 수 있으며 메시지의 발신자를 식별한다. |
| `notifications` | `users.user_id` ↔ `notifications.user_id` | 1:N | 한 사용자는 친구 요청, 매칭 완료 등 서비스에서 발생하는 여러 알림을 받을 수 있다. |
| `refresh_tokens` | `users.user_id` ↔ `refresh_tokens.user_id` | 1:1 | 각 Refresh Token은 한 사용자에게 속하며, 한 사용자는 로그인 세션에서 한 Refresh Token을 발급받을 수 있다. |
| `uploads` | `users.user_id` ↔ `uploads.user_id` | 1:N | 한 사용자는 자물쇠 생성 과정에서 여러 임시 이미지를 업로드할 수 있으며, 각 임시 업로드는 한 명의 소유 사용자에게 속한다. |
| `record_drafts` | `users.user_id` ↔ `record_drafts.user_id` | 1:N | 임시저장을 서버에서 관리하는 방식으로 확장할 경우 사용자의 작성 중인 자물쇠를 저장하기 위해 연결할 수 있다. |

---

## 2. refresh_tokens - 로그인 유지 토큰 테이블

#### 1. 테이블 설명

Access Token 재발급에 사용되는 Refresh Token의 유효 상태를 서버에서 관리하는 테이블이다. 사용자별 로그인 세션을 구분하고 토큰의 만료 및 폐기를 관리한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `refresh_token_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | Refresh Token 레코드의 고유 ID | 토큰 레코드를 고유하게 식별하며 향후 데이터 증가를 고려하여 `BIGINT`를 사용한다. | 각 로그인 세션의 Refresh Token 레코드를 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK, UK | Refresh Token을 발급받은 사용자 ID | `users.user_id`와 동일한 데이터 타입을 사용한다. 한 사용자당 하나의 Refresh Token만 유지하는 정책을 보장하기 위해 UNIQUE를 적용한다. | 어떤 사용자의 Refresh Token인지 식별하며, 동일 사용자의 Refresh Token 레코드가 여러 개 생성되지 않도록 한다. |
| `token_hash` | `VARCHAR` | 64 | NOT NULL | - | UK | Refresh Token을 SHA-256으로 해시한 값 | Refresh Token 원문 대신 SHA-256 digest를 hexadecimal 문자열로 저장한다. NIST FIPS 180-4에서 SHA-256의 message digest 크기는 256 bit로 정의되며, 이는 32 byte이다. 1 byte를 hexadecimal 2자리로 표현하므로 총 64자가 되어 `VARCHAR(64)`을 사용한다. | 사용자가 제출한 Refresh Token을 동일한 방식으로 해시한 뒤 저장된 값과 비교하여 서버가 관리하는 유효한 토큰인지 확인한다. |
| `expires_at` | `DATETIME` | - | NOT NULL | - | - | Refresh Token의 만료 시각 | 날짜와 시간이 모두 필요하므로 `DATETIME` 계열을 사용한다. | 토큰의 유효기간이 지났는지 판단하고 만료된 토큰의 사용을 차단한다. |
| `revoked_at` | `DATETIME` | - | NULL | NULL | - | Refresh Token이 폐기된 시각 | 폐기되지 않은 토큰은 값이 존재하지 않으므로 NULL을 허용한다. | 로그아웃 등으로 토큰이 더 이상 유효하지 않게 된 시점을 기록한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | Refresh Token 레코드 생성 시각 | 토큰이 발급되어 저장된 시점을 기록하기 위해 날짜와 시간을 저장한다. | 토큰 발급 시점을 추적한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_refresh_tokens` | PRIMARY KEY | `refresh_token_id` | - | 각 Refresh Token 레코드를 고유하게 식별한다. |
| `fk_refresh_tokens_user` | FOREIGN KEY | `user_id` | `users.user_id` | Refresh Token이 실제 사용자에게 속하도록 사용자 테이블과 연결한다. |
| `uk_refresh_tokens_user` | UNIQUE | `user_id` | - | 한 사용자에게 Refresh Token 레코드가 두 개 이상 생성되지 않도록 하여 사용자와 Refresh Token의 1:1 관계를 보장한다. |
| `uk_refresh_tokens_token_hash` | UNIQUE | `token_hash` | - | 동일한 Refresh Token 값이 여러 토큰 레코드에 중복 저장되지 않도록 한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `refresh_tokens.user_id` ↔ `users.user_id` | 0..1:1 | 각 Refresh Token은 반드시 한 사용자에게 속하며, 한 사용자는 동시에 최대 하나의 Refresh Token만 가질 수 있다. 로그인하지 않았거나 로그아웃한 사용자는 Refresh Token을 가지지 않을 수 있다. |

#### 5. 설계 근거

- SHA-256 digest 길이 근거: NIST FIPS 180-4, Secure Hash Standard (SHS), SHA-256 Message Digest Size = 256 bits.
- 256 bit = 32 byte이며 hexadecimal 표현은 byte당 2문자를 사용하므로 저장 문자열 길이는 64자이다.
- 참고: https://doi.org/10.6028/NIST.FIPS.180-4

---

## 3. places - 장소 테이블

#### 1. 테이블 설명

자물쇠가 기록된 좌표와 법정동 정보를 저장한다. 자물쇠 생성 시 프론트는 브라우저 Geolocation API로 최초 위치를 얻고 Kakao Maps SDK 지도에서 사용자가 수정한 최종 좌표를 확정한다. 이후 Kakao Maps SDK `services.Geocoder.coord2RegionCode()`로 법정동(`region_type=B`)을 조회해 `code`와 `region_3depth_name`을 각각 `legalDongCode`, `legalDongName`으로 백엔드에 전달한다. 정상 조회 결과 법정동이 없는 좌표(예: 일부 해상 좌표)는 두 값을 모두 NULL로 전달·저장할 수 있다. 개별 지도 마커와 상세 화면은 `place_id` 및 좌표를 기준으로 유지하고, 대시보드 지역 집계는 `legal_dong_code`를 기준으로 수행한다. `legal_dong_name`은 사용자 화면 표시용이며 NULL이면 화면에서 `확인할 수 없음`으로 표현한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `place_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 장소 고유 ID | 개별 장소 식별 |
| `legal_dong_code` | `VARCHAR` | 20 | NULL | `NULL` | IDX | 법정동 코드 | Kakao `region_type=B` 결과의 `code`. 값이 있으면 대시보드 지역 그룹의 기준 키이며, 법정동 미확인 시 NULL |
| `legal_dong_name` | `VARCHAR` | 100 | NULL | `NULL` | - | 법정동 3depth 표시명 | Kakao `region_type=B` 응답의 `region_3depth_name`. 법정동 미확인 시 NULL이며 화면에서는 `확인할 수 없음`으로 표시 |
| `latitude` | `DECIMAL` | 11,7 | NOT NULL | - | - | 위도 | 지도 표시 및 장소 좌표 식별 |
| `longitude` | `DECIMAL` | 10,7 | NOT NULL | - | - | 경도 | 지도 표시 및 장소 좌표 식별 |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 생성 시각 | 생성 추적 |
| `updated_at` | `DATETIME` | - | NULL | `NULL` | - | 최종 수정 시각 | 메타데이터 수정 추적 |

#### 3. 제약조건 및 인덱스

| 이름 | 유형 | 컬럼 | 설명 |
| --- | --- | --- | --- |
| `pk_places` | PRIMARY KEY | `place_id` | 장소 식별 |
| `idx_places_legal_dong_code` | INDEX | `legal_dong_code` | 법정동 기준 집계/조회 지원 |
| `uk_places_coordinates` | UNIQUE | `latitude`, `longitude` | V4 기준 동일 좌표 장소 중복 방지 |
| `chk_places_legal_dong_pair` | CHECK | `legal_dong_code`, `legal_dong_name` | 두 값이 모두 NULL이거나 모두 NOT NULL이어야 한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_place_location_frontend` | 프론트는 브라우저 Geolocation API로 최초 좌표를 얻고 Kakao Maps SDK 지도에서 사용자가 수정한 최종 `latitude`/`longitude`를 확정한다. |
| `rule_place_legal_dong_frontend` | 프론트는 최종 좌표에 대해 Kakao Maps SDK `services.Geocoder.coord2RegionCode()`를 호출하고 `region_type=B` 결과의 `code`와 `region_3depth_name`을 각각 `legalDongCode`, `legalDongName`으로 백엔드에 전달한다. |
| `rule_place_legal_dong_nullable_pair` | Kakao SDK 호출은 정상 성공했으나 `region_type=B` 결과가 없는 경우 `legal_dong_code`, `legal_dong_name`을 모두 NULL로 저장할 수 있다. 둘 중 하나만 NULL인 상태는 허용하지 않는다. |
| `rule_place_kakao_sdk_failure` | Kakao Maps SDK 법정동 조회 호출 자체가 실패한 경우 이를 법정동 없음으로 간주하지 않는다. 프론트는 자물쇠 생성 요청을 중단하거나 재시도한다. |
| `rule_dashboard_group_by_legal_dong_code` | 대시보드는 `legal_dong_code` 기준으로 그룹화하고 `legal_dong_name`은 표시용으로 사용한다. `legal_dong_code IS NULL`인 장소들은 하나의 미확인 그룹으로 집계하며 화면에는 `확인할 수 없음`으로 표시한다. |
| `rule_place_reuse_exact_coordinates` | latitude와 longitude가 모두 정확히 동일한 기존 `place`가 있는 경우에만 재사용한다. 근접 좌표는 기존 `place`로 판단하지 않으며, 동일 좌표가 없으면 새로운 `place`를 생성한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 설명 |
| --- | --- | --- | --- |
| `records` | `places.place_id` ↔ `records.place_id` | 1:N | 하나의 장소에 여러 자물쇠가 존재할 수 있다. |
| `monthly_reports` | `places.place_id` ↔ `monthly_reports.top_place_id` | 1:N | 월간 대표 장소는 개별 `place_id` 기준이며 대시보드 법정동 그룹과 별개다. |
| `record_drafts` | `places.place_id` ↔ `record_drafts.place_id` | 1:N | 서버 기반 자물쇠 임시저장을 지원할 경우 작성 중 선택된 장소를 복원하기 위해 장소를 참조할 수 있다. |

---

## 4. music_tracks - 음악 정보 테이블

#### 1. 테이블 설명

음악의 기본 정보를 저장하는 테이블이다. 자물쇠에 기록할 음악을 식별하고 음악 정보 표시, 인기 음악 집계, 음악 추천 등의 기능에서 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `music_track_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 뮤로 내부에서 음악을 식별하는 고유 ID | 음악 데이터를 고유하게 식별하고 향후 데이터 증가에 따른 ID 범위 확장성을 고려하여 `BIGINT`를 사용한다. | 다른 테이블에서 특정 음악을 참조하기 위한 기준 ID로 사용한다. |
| `external_track_id` | `VARCHAR` | 22 | NOT NULL | - | UK | 외부 음악 서비스에서 제공하는 음악의 고유 ID | Spotify 공식 개발자 문서기준: “Spotify ID는 Spotify URI 끝에 붙는 **Base62 식별자**이며, 128비트 UUID/해시 값을 62진법(대소문자 영문 52자 + 숫자 10자)으로 압축하여 **정확히 22자의 고정 길이**를 갖는다" | 외부 음악 서비스의 음악과 뮤로 내부 음악 데이터를 연결하고 동일 음악을 식별한다. |
| `title` | `VARCHAR` | 255 | NOT NULL | - | - | 곡 제목 | 음원 유통 메타데이터 표준 (DDEX) 규격 기준 | 자물쇠, 추천 결과, 인기 음악 등에서 곡 제목을 표시한다. |
| `artist_name` | `VARCHAR` | 255 | NOT NULL | - | - | 아티스트명 | 음원 유통 메타데이터 표준 (DDEX) 규격 기준 | 사용자에게 음악의 아티스트 정보를 표시한다. |
| `album_image_url` | `VARCHAR` | 255 | NOT NULL | - | - | 앨범 이미지 URL | 저작권 및 정책으로 Spotify가 제공하는 이미지url을 사용해야한다. Spotify에서 제공하는 앨범 이미지 URL을 저장하며, URL 길이의 변동 가능성을 고려하여 `VARCHAR(255)`를 사용한다. | 자물쇠나 음악 추천 결과에서 앨범 이미지를 표시한다. |
| `external_url` | `VARCHAR` | 255 | NOT NULL | - | - | Spotify에서 해당 곡을 확인하거나 재생하기 위한 외부 페이지 URL | 외부 URL은 길이가 가변적이므로 `VARCHAR`를 사용한다. | 사용자가 해당 음악을 외부 음악 서비스에서 확인하거나 재생할 수 있도록 연결한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 음악 정보가 뮤로에 최초 저장된 시각 | 날짜와 시간이 모두 필요하므로 `DATETIME` 계열을 사용한다. | 음악 데이터가 최초 등록된 시점을 기록한다. |
| `updated_at` | `DATETIME` | - | NULL | `NULL` | - | 음악 정보가 마지막으로 수정된 시각 | 최초 생성 이후 실제 음악 메타데이터 수정이 발생한 마지막 시각을 저장한다. 생성 후 수정 이력이 없는 경우 `NULL`을 유지하여 `created_at`과 역할을 구분한다. | 외부 음악 정보가 실제로 갱신된 마지막 시점을 추적한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_music_tracks` | PRIMARY KEY | `music_track_id` | - | 각 음악 데이터를 뮤로 내부에서 고유하게 식별한다. |
| `uk_music_tracks_external_track_id` | UNIQUE | `external_track_id` | - | 동일한 외부 음악이 `music_tracks`에 중복 저장되지 않도록 한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `records` | `music_tracks.music_track_id` ↔ `records.music_track_id` | 1:N | 하나의 음악은 여러 사용자의 여러 자물쇠에 기록될 수 있으며, 각 자물쇠는 하나의 음악을 참조한다. |
| `recommendation_playlist_items` | `recommendation_playlist_items.music_track_id` ↔ `music_tracks.music_track_id` | 1:N | 하나의 추천 항목은 하나의 음악을 참조하며, 동일한 음악이 여러 사용자의 추천 플레이리스트에 포함될 수 있다. |

---

## 5. records - 자물쇠(음악기록핀) 테이블

#### 1. 테이블 설명

사용자가 특정 장소에서 음악, 날씨, 기분, 코멘트 등의 정보를 기록한 자물쇠 데이터를 저장하는 테이블이다. 지도 표시, 사용자 자물쇠 지역 그룹, 월간 리포트, 음악 추천 및 친구 간 자물쇠 조회 등의 핵심 기능에서 사용한다. 사용자 자물쇠 지역 그룹은 별도 dashboard 테이블을 만들지 않고 `records.place_id → places.legal_dong_code` 경로를 이용해 현재 사용자의 삭제되지 않은 자물쇠를 법정동 코드 단위로 조회·집계한다. 

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `record_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 자물쇠 고유 ID | 자물쇠 데이터를 고유하게 식별하고 향후 데이터 증가에 따른 ID 범위 확장성을 고려하여 `BIGINT`를 사용한다. | 자물쇠 한 건을 식별하고 사진 등 관련 데이터가 해당 자물쇠를 참조하는 기준이 된다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 자물쇠를 생성한 사용자 ID | `users.user_id`와 동일한 타입을 사용하여 작성자와 자물쇠를 연결한다. | 자물쇠의 소유자를 식별하고 본인의 자물쇠 조회·수정·삭제 권한 판단 등에 사용한다. |
| `place_id` | `BIGINT` | - | NOT NULL | - | FK | 자물쇠가 기록된 장소 ID | 장소 정보는 `places`에서 별도로 관리하므로 해당 장소의 PK를 참조한다. | 지도에서 자물쇠 위치를 표시하고 장소·지역 기준 조회 및 집계에 사용한다. |
| `music_track_id` | `BIGINT` | - | NOT NULL | - | FK | 자물쇠에 기록된 음악 ID | 음악 기본 정보를 중복 저장하지 않고 `music_tracks`의 음악을 참조하기 위해 FK를 사용한다. | 해당 자물쇠에 어떤 음악이 기록되었는지 식별하고 인기 음악 집계·추천 등에 활용한다. |
| `weather_condition` | `ENUM` | - | NULL | `NULL` | - | 자물쇠 생성 당시의 날씨 상태 | 외부 날씨 API 응답을 그대로 저장하지 않고 백엔드에서 MULO 내부 날씨 분류값으로 변환하여 저장한다. 날씨 상태는 정해진 값의 집합으로 관리하므로 `ENUM`을 사용한다. V1에서 허용하는 값은 `CLEAR`, `CLOUDY`, `OVERCAST`, `RAIN`, `SNOW`, `RAIN_SNOW`, `SHOWER`이다. | 과거 기록 표시 및 날씨 기반 리포트 등에 활용한다. 자물쇠 생성 시 날씨 정보를 자동 조회하며 날씨 확보 실패 시 기록 생성 자체는 실패시키지 않고 `NULL`로 저장한다. |
| `temperature` | `DECIMAL` | 3, 1 | NULL | - | - | 자물쇠 생성 당시의 기온 | 기온은 음수와 소수점 이하 값을 포함할 수 있으므로 숫자형인 `DECIMAL`을 사용한다. `DECIMAL(3,1)`은 소수점 이하 1자리까지 표현하며 `-99.9 ~ 99.9` 범위의 값을 저장할 수 있어 국내 기온 데이터를 충분히 수용할 수 있다. | 자물쇠 생성 시 기온 정보를 자동 조회하여 저장하며, 외부 날씨 정보 조회에 실패할 수 있으므로 `NULL`을 허용한다. |
| `mood_score` | `TINYINT` | - | NOT NULL | -
( 화면 초기값 0 → 항상 요청 body에 mood_score 전달 ) | - | 사용자가 선택한 기분 점수 | 기분은 -50~50 범위의 정수값으로 저장하므로 MySQL TINYINT 범위 안에 충분히 포함된다. 문자열 기분값 대신 수치형으로 저장하여 평균 계산 및 구간별 대표 이모지 매핑에 활용하기 쉽다. | 자물쇠 생성 당시 사용자의 기분을 수치로 기록하고 월간 평균 기분 계산, 기준으로 활용한다. |
| `comment` | `VARCHAR` | 80 | NULL | NULL | - | 사용자가 작성한 자물쇠 코멘트 | 현재 요구사항에서 최대 80자의 짧은 문장이므로 `VARCHAR(80)`으로 충분하다. | 사용자가 해당 순간에 대한 짧은 기록을 남길 수 있도록 한다. |
| `deleted_at` | `DATETIME` | - | NULL | NULL | - | 자물쇠가 삭제된 시각 | 삭제되지 않은 자물쇠에는 값이 없으므로 NULL을 허용한다. 논리 삭제 정책을 사용하는 경우에만 필요하다. | 삭제 시점 추적 및 AI 검색·리포트 등에서 삭제 데이터 제외 여부를 판단하는 데 활용한다. |
| `created_at` | `DATETIME` | - | NOT NULL | CURRENT_TIMESTAMP | - | 자물쇠 생성 시각 | 날짜와 시간이 모두 필요하므로 `DATETIME` 계열을 사용한다. | 최신순 정렬, 월간 리포트 기간 산정, 기간 기반 조회 및 월간 리포트 산정 등에 사용한다. |
| `updated_at` | `DATETIME` | - | NULL | `NULL` | - | 자물쇠가 마지막으로 수정된 시각 | 최초 생성 이후 코멘트 등 실제 수정이 발생한 마지막 시각을 저장한다. 생성 후 수정 이력이 없는 경우 `NULL`을 유지하여 `created_at`과 역할을 구분한다. | 자물쇠 정보의 최종 수정 시점을 기록한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_records` | PRIMARY KEY | `record_id` | - | 각 자물쇠를 고유하게 식별한다. |
| `fk_records_user` | FOREIGN KEY | `user_id` | `users.user_id` | 자물쇠가 실제 사용자에게 속하도록 작성자와 연결한다. |
| `fk_records_place` | FOREIGN KEY | `place_id` | `places.place_id` | 자물쇠가 기록된 장소와 연결한다. |
| `fk_records_music_track` | FOREIGN KEY | `music_track_id` | `music_tracks.music_track_id` | 자물쇠에 저장된 음악과 연결한다. |
| `ck_records_mood_score` | CHECK | `mood_score` | - | `CHECK (mood_score BETWEEN -50 AND 50)`를 적용하여 사용자의 기분 점수가 서비스에서 정의한 -50~50 범위를 벗어나지 않도록 한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_record_photo_required` | 자물쇠 생성 시 사진은 필수이다. 생성 요청에는 현재 사용자가 소유한 유효한 `uploadId`가 반드시 포함되어야 하며, 사진 연결에 실패하면 자물쇠 생성을 완료하지 않는다. |
| `rule_record_photo_single` | 하나의 자물쇠에는 사진 한 장만 연결한다. 최종 사진 정보는 `record_photos`에서 관리한다. |
| `rule_popular_records_recent_7d` | 홈의 인기 자물쇠 지도/목록은 조회 시점 기준 `records.created_at >= 조회 시점 - 7일`이고 `deleted_at IS NULL`인 자물쇠만 대상으로 한다. 최근 7일 내 자물쇠가 없는 장소는 인기 자물쇠 마커 대상에서 제외한다. |
| `rule_popular_track_ranking` | 인기 자물쇠 마커 선택 후 음악 랭킹은 같은 최근 7일 자물쇠만 `music_track_id`별로 집계한다. 1차 정렬은 저장 횟수(`recordCount`) 내림차순, 동률 시 `music_track_id` 오름차순이다. |
| `rule_my_records_all_time` | 내 자물쇠 지도/목록에는 최근 7일 제한을 적용하지 않는다. 현재 사용자의 `deleted_at IS NULL`인 모든 자물쇠가 조회 대상이다. |
| `rule_map_cluster_multi_place` | 지도 축소로 여러 장소 마커가 하나의 클러스터로 묶이면 클러스터에 포함된 모든 `place_id`를 `placeIds` 목록으로 조회할 수 있어야 한다. 인기/내 자물쇠 모두 동일하다. |
| `rule_popular_marker_count` | 인기 자물쇠 지도 마커의 `recordsCount`는 해당 장소에서 조회 시점 기준 최근 7일 이내 생성되고 삭제되지 않은 자물쇠 수다. |
| `rule_my_marker_count` | 내 자물쇠 지도 마커의 `myRecordsCount`는 해당 장소에 저장된 현재 사용자의 전체 삭제되지 않은 자물쇠 수이며 7일 제한을 적용하지 않는다. |
| `rule_user_record_region_group` | 현재 사용자의 자물쇠 지역 폴더는 `places.legal_dong_code` 기준으로 그룹화하고 `places.legal_dong_name`을 표시명으로 사용한다. `legal_dong_code IS NULL`은 하나의 미확인 그룹으로 묶어 UI에서 `확인할 수 없음`으로 표시한다. |
| `rule_user_record_region_detail` | 지역 폴더 선택 시 해당 법정동 그룹에 속한 현재 사용자의 삭제되지 않은 자물쇠 목록을 조회하며, 자물쇠 선택 시 기존 자물쇠 상세 조회를 사용한다. |
| `rule_cursor_page_size` | 커서 기반 목록 조회의 `size` 기본값은 20, 최대값은 100이다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `records.user_id` ↔ `users.user_id` | N:1 | 각 자물쇠는 한 사용자가 생성하며, 한 사용자는 여러 자물쇠를 생성할 수 있다. |
| `places` | `records.place_id` ↔ `places.place_id` | N:1 | 각 자물쇠는 하나의 장소를 참조하며, 하나의 장소에는 여러 자물쇠가 기록될 수 있다. |
| `music_tracks` | `records.music_track_id` ↔ `music_tracks.music_track_id` | N:1 | 각 자물쇠는 하나의 음악을 참조하며, 하나의 음악은 여러 자물쇠에서 사용될 수 있다. |
| `record_photos` | `records.record_id` ↔ `record_photos.record_id` | 1:1 | 서비스 정책상 자물쇠 생성 완료 시 사진 한 장이 반드시 연결된다. `record_photos.record_id`의 UNIQUE 제약은 하나의 자물쇠에 두 장 이상의 사진이 연결되는 것을 방지한다. |

---

## 6. record_photos - 자물쇠 사진

#### 1. 테이블 설명

자물쇠에 최종 연결된 사진 정보를 저장하는 테이블이다. 실제 이미지 파일은 Google Cloud Storage에 저장하고, DB에는 자물쇠와 연결된 이미지의 저장 위치와 파일 메타데이터를 저장한다. 자물쇠 상세 조회, 지도·대시보드의 사진 표시 및 AI 사진 분석 등에 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `record_photo_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 자물쇠 사진 레코드의 고유 ID | 사진 데이터를 고유하게 식별하고 향후 사진 데이터 증가에 따른 ID 범위 확장성을 고려하여 `BIGINT`를 사용한다. | 개별 자물쇠 사진 정보를 식별한다. |
| `record_id` | `BIGINT` | - | NOT NULL | - | FK | 사진이 첨부된 자물쇠 ID | `records.record_id`와 동일한 타입을 사용하여 사진과 자물쇠를 연결한다. | 어떤 자물쇠에 첨부된 사진인지 식별한다. |
| `image_url` | `VARCHAR` | 255 | NOT NULL | - | - | Google Cloud Storage에 저장된 이미지의 위치 정보 | 실제 이미지 파일은 Google Cloud Storage에 저장하고 DB에는 해당 이미지를 식별하거나 접근하기 위한 경로 또는 URL을 문자열로 저장한다. | 자물쇠 사진을 조회하고 화면에 표시할 때 이미지의 저장 위치를 확인한다. |
| `mime_type` | `VARCHAR` | 50 | NOT NULL | - | - | 이미지 파일의 MIME 타입 | `image/jpeg`, `image/png`, `image/heic`, `image/webp` 등 파일 형식을 문자열로 표현하므로 `VARCHAR`를 사용한다. | 업로드된 이미지 형식을 확인하고 파일 검증·처리에 활용한다. |
| `file_size` | `BIGINT` | - | NOT NULL | - | - | 이미지 파일 크기(Byte) | 파일 크기는 정수이며 파일 크기가 커질 가능성을 충분히 수용하기 위해 `BIGINT`를 사용할 수 있다. | 이미지 업로드 용량 제한 검증 및 파일 관리에 활용한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 사진 정보가 생성된 시각 | 사진 등록 시점을 날짜와 시간으로 기록하기 위해 `DATETIME` 계열을 사용한다. | 사진이 자물쇠에 등록된 시점을 기록한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_record_photos` | PRIMARY KEY | `record_photo_id` | - | 각 자물쇠 사진 정보를 고유하게 식별한다. |
| `fk_record_photos_lock` | FOREIGN KEY | `record_id` | `records.record_id` | 사진이 실제 자물쇠에 속하도록 자물쇠와 연결한다. |
| `uk_record_photos_lock` | UNIQUE | `record_id` | - | 하나의 자물쇠에 두 장 이상의 사진이 연결되지 않도록 한다. 자물쇠 생성 시 사진 한 장을 필수로 연결하는 규칙은 서비스 계층에서 함께 보장한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_record_photo_required` | 자물쇠 생성 완료 시 사진 한 장이 반드시 연결되어야 한다. 사진 없이 자물쇠 생성을 완료하지 않는다. |
| `rule_record_photo_storage` | 실제 이미지 파일은 Google Cloud Storage에 저장하고, `record_photos`에는 최종 자물쇠에 연결된 이미지의 위치와 메타데이터를 저장한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `records` | `record_photos.record_id` ↔ `records.record_id` | 1:1 | 각 사진은 하나의 자물쇠에 속한다. 서비스 정책상 자물쇠 생성 완료 시 사진 한 장이 반드시 연결되며, `UNIQUE(record_id)`로 두 장 이상의 연결을 방지한다. |
| `uploads` | 최종 자물쇠 생성 시 `uploadId`에 해당하는 임시 업로드 정보를 사용하여 `record_photos`를 생성 | 논리적 연계 | 임시 업로드 단계에서 저장한 이미지 위치와 파일 메타데이터를 최종 자물쇠 사진 정보로 연결한다. 직접 FK 관계는 두지 않는다. |

---

## 7. uploads - 임시 이미지 업로드 테이블

#### 1. 테이블 설명

자물쇠 생성 전에 업로드된 임시 이미지의 메타데이터를 저장하는 테이블이다. 실제 이미지 파일은 Google Cloud Storage에 저장하며, 백엔드는 `upload_id`를 통해 업로드 소유 사용자와 이미지 저장 위치를 식별한다. 발급된 `uploadId`는 사진 기반 AI 음악 추천과 최종 자물쇠 생성에서 재사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `upload_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 임시 업로드 고유 ID | `/api/uploads`가 `uploadId: Long`을 발급하므로 다른 서비스 내부 식별자와 동일하게 `BIGINT`를 사용한다. | 사진 기반 음악 추천과 최종 자물쇠 생성에서 동일한 임시 이미지를 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 임시 이미지를 업로드한 사용자 ID | 임시 업로드는 인증된 사용자가 소유하는 리소스이며, `users.user_id`와 연결하여 소유권을 확인한다. | 다른 사용자의 `uploadId` 사용을 방지하고 현재 사용자 소유 업로드인지 검증한다. |
| `image_url` | `VARCHAR` | 255 | NOT NULL | - | - | Google Cloud Storage에 저장된 임시 이미지의 위치 정보 | 실제 이미지 파일은 Google Cloud Storage에 저장하고 DB에는 해당 이미지를 식별하거나 접근하기 위한 경로 또는 URL을 문자열로 저장한다. | AI 사진 추천 및 최종 자물쇠 생성 시 사용할 이미지의 저장 위치를 확인한다. |
| `mime_type` | `VARCHAR` | 50 | NOT NULL | - | - | 이미지 파일의 MIME 타입 | 업로드 API에서 JPG, JPEG, PNG, HEIC, WebP 이미지를 허용하며 파일 형식을 서버에서 검증하므로 MIME 타입을 저장한다. | 파일 형식 확인 및 최종 `record_photos` 메타데이터 생성에 사용한다. |
| `file_size` | `BIGINT` | - | NOT NULL | - | - | 이미지 파일 크기(Byte) | 업로드 API는 최대 10MB 제한을 적용하며 파일 크기는 정수이므로 `BIGINT`를 사용한다. | 업로드 용량 검증 및 최종 사진 메타데이터 생성에 사용한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 임시 업로드 생성 시각 | 임시 업로드가 생성된 시점을 기록한다. | 미연결 임시 업로드 cleanup 대상 판단의 기준 시각으로 사용한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_uploads` | PRIMARY KEY | `upload_id` | - | 각 임시 업로드를 고유하게 식별한다. |
| `fk_uploads_user` | FOREIGN KEY | `user_id` | `users.user_id` | 임시 업로드가 실제 사용자에게 속하도록 소유 사용자와 연결한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_upload_owner` | `uploadId`는 현재 로그인한 사용자가 소유한 유효한 임시 업로드여야 한다. 존재하지 않거나, 다른 사용자 소유이거나, 생성 후 1시간이 지나 만료된 경우 동일하게 `UPLOAD_NOT_FOUND`로 처리한다. |
| `rule_upload_reuse` | 동일한 `uploadId`를 유효시간 내 사진 기반 AI 음악 추천과 최종 자물쇠 생성에서 재사용한다. |
| `rule_upload_required_for_record` | 자물쇠 생성 시 사진이 필수이므로 유효한 `uploadId`를 반드시 전달해야 한다. |
| `rule_upload_validity` | 임시 업로드는 생성 시점부터 1시간 동안 유효하다. 유효시간이 지난 업로드는 사진 기반 음악 추천 및 자물쇠 생성에 사용할 수 없다. |
| `rule_upload_cleanup` | 생성 후 1시간이 지나도록 최종 자물쇠에 연결되지 않은 임시 업로드는 `uploads` 레코드와 Google Cloud Storage의 임시 이미지 cleanup 대상이다. |
| `rule_upload_finalize` | 자물쇠 생성 성공 시 임시 업로드의 이미지 위치 및 메타데이터를 `record_photos`에 저장하고 해당 `uploads` 레코드는 삭제한다. |
| `rule_upload_format` | 허용 이미지 형식은 JPG, JPEG, PNG, HEIC, WebP이며 최대 파일 크기는 10MB이다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `uploads.user_id` ↔ `users.user_id` | N:1 | 한 사용자는 여러 임시 이미지를 업로드할 수 있으며, 각 임시 업로드는 한 사용자에게 속한다. |
| `record_photos` | `uploadId`를 이용한 최종 자물쇠 생성 처리 | 논리적 연계 | 자물쇠 생성 시 임시 업로드의 이미지 위치와 파일 메타데이터를 사용하여 최종 `record_photos` 데이터를 생성한다. 직접 FK 관계는 두지 않는다. |

---

## 8. record_drafts - 자물쇠 임시저장 [V1 제외]

#### 1. 테이블 설명

> **상태: V1 제외** — 기존 설계 내용은 이력 보존을 위해 유지하며 V1 구현 대상에서는 제외한다.

자물쇠 작성 중인 내용을 서버에 임시 저장하여 이후 다시 불러올 수 있도록 하기 위한 테이블이다.

( V1에서는 브라우저 로컬 저장소를 이용하므로 사용하지 않으며, 향후 계정 기반 임시저장 및 다른 기기·브라우저 간 임시저장 동기화를 지원할 경우 도입을 검토한다. )

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `record_draft_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 임시저장 데이터 고유 ID | 임시저장 데이터를 고유하게 식별하기 위한 숫자형 PK이다. | 임시저장 한 건을 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 임시저장을 작성한 사용자 ID | `users.user_id`를 참조하여 임시저장 데이터의 소유자를 식별한다. | 어떤 사용자의 임시저장인지 확인한다. |
| `place_id` | `BIGINT` | - | NULL | NULL | FK | 작성 중 선택된 장소 ID | 임시저장 시점에는 장소 선택이 완료되지 않았을 수 있으므로 NULL을 허용한다. | 작성 화면 복원 시 선택했던 장소를 복원한다. |
| `music_track_id` | `BIGINT` | - | NULL | NULL | FK | 작성 중 선택된 음악 ID | 음악 선택 전에도 임시저장이 가능할 수 있으므로 NULL을 허용한다. | 선택했던 음악을 복원한다. |
| `mood_score` | `TINYINT` | - | NOT NULL | - | - | 작성 중 선택한 기분 | 기분은 `-50 ~ 50` 범위의 정수값으로 저장한다. 생성 화면의 초기 기분값은 애플리케이션에서 `0`으로 설정하며, 임시저장 시 현재 기분값을 명시적으로 저장하므로 DB 기본값은 두지 않는다. | 임시저장 시점의 기분값을 보존하여 작성 화면 복원 시 사용한다. |
| `comment` | `VARCHAR` | 80 | NULL | NULL | - | 작성 중 입력한 코멘트 | 자물쇠의 코멘트 최대 길이와 동일하게 관리한다. | 작성 중이던 코멘트를 복원한다. |
| `photo_url` | `VARCHAR` | 255 | NULL | NULL | - | 임시 업로드된 사진 위치 | 사진이 선택되지 않았을 수 있으므로 NULL을 허용한다. | 임시저장 복원 시 사진을 다시 표시한다. |
| `expires_at` | `DATETIME` | - | NULL | NULL | - | 임시저장 데이터 만료 시각 | 서버에 오래된 임시 데이터를 계속 보관하지 않도록 만료 정책을 적용할 경우 사용한다. | 만료된 임시저장 데이터를 정리하는 기준이 된다. |
| `created_at` | `DATETIME` | - | NOT NULL | CURRENT_TIMESTAMP | - | 임시저장 생성 시각 | 임시저장이 처음 생성된 시점을 기록한다. | 임시저장 생성 시점을 관리한다. |
| `updated_at` | `DATETIME` | - | NOT NULL | CURRENT_TIMESTAMP | - | 마지막 임시저장 시각 | 작성 내용이 갱신된 마지막 시점을 기록한다. | 가장 최근 작성 상태와 임시저장 시점을 관리한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_record_drafts` | PRIMARY KEY | `record_draft_id` | - | 각 임시저장 데이터를 고유하게 식별한다. |
| `fk_record_drafts_user` | FOREIGN KEY | `user_id` | `users.user_id` | 임시저장 데이터의 소유 사용자와 연결한다. |
| `fk_record_drafts_place` | FOREIGN KEY | `place_id` | `places.place_id` | 임시저장된 장소가 있는 경우 해당 장소와 연결한다. |
| `fk_record_drafts_music_track` | FOREIGN KEY | `music_track_id` | `music_tracks.music_track_id` | 임시 선택된 음악이 있는 경우 해당 음악과 연결한다. |
| `chk_record_drafts_mood_score` | CHECK | `mood_score` | - | `mood_score`가 서비스에서 정의한 `-50 ~ 50` 범위를 벗어나지 않도록 `CHECK (mood_score BETWEEN -50 AND 50)`를 적용한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `record_drafts.user_id` ↔ `users.user_id` | N:1 | 하나의 임시저장은 한 사용자에게 속하며, 서버 임시저장 정책에 따라 한 사용자가 여러 임시저장을 가질 수 있다. |
| `places` | `record_drafts.place_id` ↔ `places.place_id` | N:1 | 임시저장 중 장소를 선택한 경우 하나의 장소를 참조하며, 같은 장소가 여러 임시저장에서 사용될 수 있다. |
| `music_tracks` | `record_drafts.music_track_id` ↔ `music_tracks.music_track_id` | N:1 | 임시저장 중 음악을 선택한 경우 하나의 음악을 참조하며, 같은 음악이 여러 임시저장에서 사용될 수 있다. |

---

## 9. friend_requests - 친구 요청 테이블

#### 1. 테이블 설명

사용자 간의 **현재 대기 중인 친구 요청**을 저장하는 테이블이다. 데이터가 존재한다는 것 자체가 아직 처리되지 않은 친구 요청을 의미한다. 요청이 수락되면 friendships 테이블에 친구 관계를 생성한 뒤 해당 요청을 삭제하고, 거절 또는 요청 취소 시에는 요청 데이터만 삭제한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `friend_request_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 친구 요청 고유 ID | 각 친구 요청을 고유하게 식별하고 향후 요청 데이터 증가를 고려하여 `BIGINT`를 사용한다. | 하나의 친구 요청 데이터를 식별한다. |
| `requester_id` | `BIGINT` | - | NOT NULL | - | FK | 친구 요청을 보낸 사용자 ID | `users.user_id`와 동일한 타입을 사용하여 요청자를 참조한다. | 누가 친구 요청을 보냈는지 식별한다. |
| `addressee_id` | `BIGINT` | - | NOT NULL | - | FK | 친구 요청을 받은 사용자 ID | `users.user_id`와 동일한 타입을 사용하여 요청 대상 사용자를 참조한다. | 누가 친구 요청을 받았는지 식별한다. |
| `user_low_id` | `BIGINT` | - | NOT NULL | `GENERATED` | - | 요청 사용자 쌍 중 작은 사용자 ID | `LEAST(requester_id, addressee_id)` 값으로 자동 생성하여 요청 방향과 관계없이 동일 사용자 쌍을 같은 값으로 정규화한다. | 역방향 포함 동일 사용자 쌍의 중복 친구 요청을 방지하는 기준으로 사용한다. |
| `user_high_id` | `BIGINT` | - | NOT NULL | `GENERATED` | - | 요청 사용자 쌍 중 큰 사용자 ID | `GREATEST(requester_id, addressee_id)` 값으로 자동 생성하여 요청 방향과 관계없이 동일 사용자 쌍을 같은 값으로 정규화한다. | 역방향 포함 동일 사용자 쌍의 중복 친구 요청을 방지하는 기준으로 사용한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 친구 요청 생성 시각 | 요청 생성 시점을 기록하기 위해 날짜와 시간을 저장한다. | 친구 요청 목록 정렬 및 요청 생성 시점 확인에 사용한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_friend_requests` | PRIMARY KEY | `friend_request_id` | - | 각 친구 요청을 고유하게 식별한다. |
| `fk_friend_requests_requester` | FOREIGN KEY | `requester_id` | `users.user_id` | 친구 요청을 보낸 사용자가 실제 존재하는 사용자인지 보장한다. |
| `fk_friend_requests_addressee` | FOREIGN KEY | `addressee_id` | `users.user_id` | 친구 요청을 받은 사용자가 실제 존재하는 사용자인지 보장한다. |
| `chk_friend_requests_not_self` | CHECK | `requester_id`, `addressee_id` | - | `CHECK (requester_id <> addressee_id)`를 적용하여 자기 자신에게 친구 요청을 보내는 것을 방지한다. |
| `uk_friend_requests_user_pair` | UNIQUE | `user_low_id`, `user_high_id` | - | 요청 방향과 관계없이 동일한 두 사용자 사이에 대기 중인 친구 요청이 두 개 이상 존재하지 않도록 한다. |

역방향 요청 처리는 비즈니스 규칙으로 관리하며, `user_low_id`, `user_high_id`에 대한 UNIQUE 제약으로 동일 사용자 쌍의 정방향·역방향 요청이 동시에 중복 저장되는 것을 방지한다.

#### **3-1. 비즈니스 규칙**

| 규칙명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `rule_friend_requests_reverse` | 비즈니스 규칙 | `requester_id`, `addressee_id` | `friend_requests` | `A → B` 요청이 존재할 때 `B → A` 요청이 들어오면 새로운 요청을 생성하지 않고, 기존 친구 요청을 삭제한 뒤 `friendships`에 두 사용자 간 친구 관계를 생성한다. |
| `rule_friend_requests_existing_friend` | 비즈니스 규칙 | `requester_id`, `addressee_id` | `friendships` | 두 사용자가 이미 친구 관계라면 새로운 친구 요청을 생성하지 않는다. |

#### 4. 관계

| FK 컬럼 | 참조 테이블 | 참조 컬럼 | 관계 |
| --- | --- | --- | --- |
| `requester_id` | `users` | `user_id` | users 1 : N friend_requests |
| `addressee_id` | `users` | `user_id` | users 1 : N friend_requests |

#### 5. 설계 설명

| 항목 내용 |  |
| --- | --- |
| **테이블 설계 이유** | 친구 요청은 요청자와 수신자가 구분되는 방향성이 있는 데이터이므로, 방향성이 없는 실제 친구 관계와 분리하여 관리한다. `friend_requests`에는 현재 처리 대기 중인 요청만 저장하며, 수락·거절·취소가 완료되면 해당 요청 데이터는 삭제한다. |
| **주요 관계 설계 이유** | 요청자와 수신자 모두 `users`에 존재하는 사용자이므로 각각 FK로 연결한다. 요청이 수락되면 `friendships`에 실제 양방향 친구 관계를 생성한다. |
| **주요 데이터 타입 선정 이유** | 사용자 식별자는 `users.user_id`와 동일하게 `BIGINT`를 사용한다. 친구 요청 생성 시점은 날짜와 시간이 필요하므로 `created_at`에 `DATETIME`을 사용한다. |
| **주요 제약조건 설정 이유** | 요청자와 수신자는 반드시 존재하는 사용자여야 하므로 FK를 설정한다. 자기 자신에게 요청할 수 없도록 CHECK 제약을 두며, `UNIQUE(user_low_id, user_high_id)`를 통해 요청 방향과 관계없이 동일한 두 사용자 사이에 대기 중인 친구 요청이 중복 생성되는 것을 방지한다. |
| **동시성 고려** | `A → B`와 `B → A` 요청은 `user_low_id`, `user_high_id`가 동일한 사용자 쌍으로 계산되므로 `UNIQUE(user_low_id, user_high_id)` 제약을 통해 두 요청이 동시에 중복 저장되는 것을 방지한다. 역방향 요청이 감지되면 애플리케이션 트랜잭션에서 기존 요청을 삭제하고 `friendships`를 생성하여 친구 관계로 전환한다. |

---

## 10. friendships - 친구 관계 테이블

#### 1. 테이블

친구 요청이 수락된 이후 실제로 성립된 사용자 간 친구 관계를 저장하는 테이블이다. 친구 관계는 방향성이 없는 양방향 관계로 관리하며 친구 목록 조회 등에 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `friendship_id` | BIGINT | - | NOT NULL | AUTO_INCREMENT | PK | 친구 관계 고유 식별자 | 친구 관계 레코드를 고유하게 식별하기 위해 사용 | 친구 관계 식별 |
| `user_low_id` | BIGINT | - | NOT NULL | - | FK | 친구 관계 사용자 중 ID가 작은 사용자 | 동일 사용자 쌍을 항상 같은 순서로 저장하기 위해 사용 | 친구 관계 중복 방지 및 친구 목록 조회 |
| `user_high_id` | BIGINT | - | NOT NULL | - | FK | 친구 관계 사용자 중 ID가 큰 사용자 | 동일 사용자 쌍을 항상 같은 순서로 저장하기 위해 사용 | 친구 관계 중복 방지 및 친구 목록 조회 |
| `created_at` | DATETIME | - | NOT NULL | CURRENT_TIMESTAMP | - | 친구 관계가 성립된 시각 | 친구가 된 시점을 기록 | 친구 관계 생성 시각 확인 |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_friendships` | PRIMARY KEY | `friendship_id` | - | 각 친구 관계를 고유하게 식별한다. |
| `fk_friendships_user_low` | FOREIGN KEY | `user_low_id` | `users.user_id` | 친구 관계에 포함된 사용자가 실제 존재하는 사용자인지 보장한다. |
| `fk_friendships_user_high` | FOREIGN KEY | `user_high_id` | `users.user_id` | 친구 관계에 포함된 사용자가 실제 존재하는 사용자인지 보장한다. |
| `chk_friendships_user_order` | CHECK | `user_low_id`, `user_high_id` | - | 항상 `user_low_id < user_high_id`가 되도록 하여 자기 자신과의 친구 관계를 막고 사용자 순서를 고정한다. |
| `uk_friendships_user_pair` | UNIQUE | `user_low_id`, `user_high_id` | - | 동일한 두 사용자 사이에 친구 관계가 중복 생성되는 것을 방지한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `friendships.user_low_id` ↔ `users.user_id` | N:1 | 한 사용자는 여러 친구 관계에서 `user_low_id`로 포함될 수 있다. |
| `users` | `friendships.user_high_id` ↔ `users.user_id` | N:1 | 한 사용자는 여러 친구 관계에서 `user_high_id`로 포함될 수 있다. |
| `friend_requests` | 친구 요청 수락 결과로 `friendships` 생성 | 논리적 연계 | 요청이 수락되면 실제 친구 관계를 생성하고 기존 요청은 삭제한다. |

#### 5. 설계 설명

| 항목 | 내용 |
| --- | --- |
| **테이블 설계 이유** | 친구 요청이 수락된 이후 실제로 성립된 친구 관계만 저장하기 위해 `friend_requests`와 분리하여 설계한다. 친구 관계는 방향성이 없는 양방향 관계이므로 두 사용자 사이에 하나의 레코드만 저장한다. |
| **주요 관계 설계 이유** | 하나의 친구 관계에는 두 명의 사용자가 포함되므로 `user_low_id`, `user_high_id`를 각각 `users.user_id`에 FK로 연결한다. 두 사용자 ID를 항상 작은 값과 큰 값으로 정렬하여 저장함으로써 `A-B`와 `B-A`를 동일한 친구 관계로 표현한다. |
| **주요 데이터 타입 선정 이유** | 사용자 식별자는 `users.user_id`와 동일하게 `BIGINT`를 사용한다. 친구 관계 성립 시점은 날짜와 시간이 필요하므로 `DATETIME`을 사용한다. |
| **주요 제약조건 설정 이유** | `user_low_id < user_high_id` CHECK 제약을 통해 자기 자신과의 친구 관계를 방지하고 사용자 저장 순서를 고정한다. 또한 `UNIQUE(user_low_id, user_high_id)`를 적용하여 동일한 두 사용자 사이의 친구 관계가 중복 생성되는 것을 방지한다. |
| **친구 삭제 처리 방식** | 친구 관계를 한 행으로 관리하므로 친구 삭제 시 해당 `friendships` 레코드 하나를 삭제하면 양쪽 사용자의 친구 관계가 동시에 해제된다. 별도의 양방향 레코드를 각각 삭제할 필요가 없다. |
| **동시성 고려** | 동일한 두 사용자에 대해 친구 관계 생성 요청이 동시에 처리될 수 있으므로 애플리케이션 로직만으로 중복을 판단하지 않고 `UNIQUE(user_low_id, user_high_id)` 제약을 최종 방어선으로 사용한다. |
| **이력이 필요하다면** | 현재는 친구 삭제 시 관계 데이터를 물리적으로 삭제하는 방식을 기준으로 한다. 추후 친구 관계 이력, 삭제 시점, 재친구 요청 제한 등의 요구사항이 생기면 `deleted_at` 또는 별도 이력 테이블 도입을 검토한다. |

---

## 11. monthly_reports - 월간 리포트 테이블

#### 1. 테이블 설명

사용자의 월별 자물쇠 기록을 기반으로 생성된 월간 리포트의 대표 통계와 AI 요약 결과를 저장하는 테이블이다. 한 번 생성된 월간 리포트는 스냅샷 형태로 보존하며, 이후 원본 자물쇠 데이터가 변경되더라도 기존 리포트의 결과는 재계산하지 않는다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `monthly_report_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 월간 리포트 고유 ID | 각 리포트를 고유하게 식별하고 데이터 증가에 따른 ID 범위 확장성을 고려하여 `BIGINT`를 사용한다. | 하나의 월간 리포트를 식별하고 기분·사진 통계 등 하위 테이블이 해당 리포트를 참조하는 기준이 된다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 리포트 소유 사용자 ID | `users.user_id`를 참조하여 누구의 리포트인지 식별한다. | 사용자별 월간 리포트를 조회할 때 사용한다. |
| `report_year` | `SMALLINT` | - | NOT NULL | - | - | 리포트 대상 연도 | 연도 값은 범위가 매우 작으므로 `SMALLINT`로 충분하다. | 특정 연도의 리포트를 조회하는 기준이 된다. |
| `report_month` | `SMALLINT` | - | NOT NULL | - | - | 리포트 대상 월 | 월은 1~12의 작은 정수값이므로 `SMALLINT`를 사용한다. | 특정 월의 리포트를 조회하는 기준이 된다. |
| `record_count` | `INTEGER` | - | NOT NULL | 0 | - | 해당 월에 포함된 자물쇠 개수 | 월별 자물쇠 개수는 정수값이므로 `INTEGER`를 사용한다. | 월간 기록 개수를 리포트에 표시한다. |
| `top_place_id` | `BIGINT` | - | NULL | NULL | FK | 해당 월에 가장 많이 기록된 장소 ID | 가장 많이 기록된 장소가 존재하는 경우 `places.place_id`를 참조한다. 자물쇠가 없는 달에는 값이 없을 수 있으므로 NULL을 허용할 수 있다. | 월간 대표 장소를 표시한다. |
| `top_artist_name` | `VARCHAR` | 255 | NULL | NULL | - | 해당 월에 가장 많이 기록된 아티스트명 | 음원 유통 메타데이터 표준 (DDEX) 규격 기준 | 월간 대표 아티스트를 표시한다. |
| `ai_recap_text` | `VARCHAR` | 100 | NULL | NULL | - | AI가 생성한 월간 요약 문장 | AI가 생성한 월간 회고를 최대 100자로 저장하기 위해 `VARCHAR(100)`을 사용한다. | 월간 리포트의 AI RECAP 문장을 표시한다. |
| `ai_recap_status` | `ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')` | - | NOT NULL | PENDING | - | AI 월간 요약 생성 상태
(`PENDING`, `PROCESSING`, `COMPLETED,` `FAILED` ) | AI 요약 상태가 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` 네 가지로 제한되므로 `ENUM`을 사용한다. | AI 요약이 준비되었는지 판단하고 화면 상태를 제어한다. |
| `created_at` | `DATETIME` | - | NOT NULL | CURRENT_TIMESTAMP | - | 월간 리포트가 최초 생성·저장된 시각 | 리포트는 최초 생성 결과를 스냅샷으로 저장하므로 생성 시각을 기록한다. | 리포트의 최초 생성 시점을 관리한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_monthly_reports` | PRIMARY KEY | `monthly_report_id` | - | 각 월간 리포트를 고유하게 식별한다. |
| `fk_monthly_reports_user` | FOREIGN KEY | `user_id` | `users.user_id` | 월간 리포트의 소유 사용자와 연결한다. |
| `uk_monthly_reports_user_period` | UNIQUE | `user_id`, `report_year`, `report_month` | - | 한 사용자가 같은 연도와 월에 두 개 이상의 월간 리포트를 가지지 않도록 한다. |
| `fk_monthly_reports_top_place` | FOREIGN KEY | `top_place_id` | `places.place_id` | 월간 리포트의 대표 장소가 실제 존재하는 장소 데이터를 참조하도록 한다. |
| `chk_monthly_reports_month` | CHECK | `report_month` | - | `report_month`가 유효한 월 범위인 `1~12`를 벗어나지 않도록 `CHECK (report_month BETWEEN 1 AND 12)`를 적용한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `monthly_reports.user_id` ↔ `users.user_id` | N:1 | 하나의 월간 리포트는 한 사용자에게 속하며, 한 사용자는 여러 달에 걸쳐 여러 월간 리포트를 가질 수 있다. |
| `places` | `monthly_reports.top_place_id` ↔ `places.place_id` | N:1 | 하나의 월간 리포트는 대표 장소가 있는 경우 하나의 장소를 참조하며, 하나의 장소가 여러 사용자의 여러 월간 리포트에서 대표 장소가 될 수 있다. |
| `monthly_mood_stats` | `monthly_reports.monthly_report_id` ↔ `monthly_mood_stats.monthly_report_id` | 1:1 | 하나의 월간 리포트는 하나의 월 평균 기분 통계 데이터를 가지며, 각 월간 기분 통계는 하나의 월간 리포트에 속한다. |
| `monthly_photo_scene_stats` | `monthly_reports.monthly_report_id` ↔ `monthly_photo_scene_stats.monthly_report_id` | 1:N | 하나의 월간 리포트는 여러 사진 분위기·장면별 통계 데이터를 가진다. |

---

## 12. monthly_mood_stats - 월간 기분 통계 테이블

#### 1. 테이블 설명

월간 리포트 생성 시 해당 월에 작성된 자물쇠의 기분 점수를 집계하여 **월 평균 기분 점수**를 저장하는 테이블이다.

각 자물쇠의 기분은 `-50 ~ 50` 범위의 정수값으로 기록하며, 월간 리포트에서는 평균 기분 점수에 해당하는 대표 이모지를 표시한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `mood_stat_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 월간 기분 통계 고유 ID | 각 월간 기분 통계 데이터를 고유하게 식별하고 데이터 증가를 고려하여 `BIGINT`를 사용한다. | 하나의 월간 기분 통계 데이터를 식별한다. |
| `monthly_report_id` | `BIGINT` | - | NOT NULL | - | FK, UK | 기분 통계가 속한 월간 리포트 ID | `monthly_reports.monthly_report_id`를 참조한다. 하나의 월간 리포트에는 하나의 평균 기분 통계만 존재하므로 `UNIQUE`를 적용한다. | 월간 기분 통계와 해당 월간 리포트를 연결한다. |
| `average_mood_score` | `DECIMAL` | `3,1` | NOT NULL | - | - | 해당 월의 평균 기분 점수 | 개별 기분 점수는 정수지만 여러 자물쇠의 평균은 소수점이 발생할 수 있다. 서비스 범위가 `-50.0 ~ 50.0`이므로 `DECIMAL(3,1)`로 충분히 표현할 수 있다. | 월간 평균 기분을 나타내며 대표 이모지를 결정하는 기준으로 사용한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조/조건 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_monthly_mood_stats` | PRIMARY KEY | `mood_stat_id` | - | 각 월간 기분 통계 데이터를 고유하게 식별한다. |
| `fk_monthly_mood_stats_report` | FOREIGN KEY | `monthly_report_id` | `monthly_reports.monthly_report_id` | 기분 통계를 해당 월간 리포트와 연결한다. |
| `uk_monthly_mood_stats_report` | UNIQUE | `monthly_report_id` | - | 하나의 월간 리포트에 월 평균 기분 통계가 두 개 이상 생성되지 않도록 한다. |
| `ck_monthly_mood_stats_score` | CHECK | `average_mood_score` | `average_mood_score BETWEEN -50 AND 50` | 평균 기분 점수가 서비스에서 정의한 `-50 ~ 50` 범위를 벗어나지 않도록 한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `monthly_reports` | `monthly_mood_stats.monthly_report_id` ↔ `monthly_reports.monthly_report_id` | **1:1** | 하나의 월간 리포트에는 하나의 월 평균 기분 통계가 존재하며, 각 기분 통계는 하나의 월간 리포트에 속한다. |

---

## 13. monthly_photo_scene_stats - 월간 사진 분위기 통계 테이블

#### 1. 테이블 설명

월간 리포트 생성 시 해당 월에 기록된 자물쇠 사진을 분석하여 사진 분위기 또는 장면별 개수와 비율을 저장하는 테이블이다. 월별 상세 리포트에서 사진 분위기 분포를 표시하는 데 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `photo_scene_stat_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 월간 사진 분위기 통계 고유 ID | 각 사진 분위기 통계 행을 고유하게 식별하고 데이터 증가에 따른 ID 범위 확장성을 고려하여 `BIGINT`를 사용한다. | 하나의 사진 분위기별 통계 데이터를 식별한다. |
| `monthly_report_id` | `BIGINT` | - | NOT NULL | - | FK | 사진 분위기 통계가 속한 월간 리포트 ID | `monthly_reports.monthly_report_id`를 참조하여 어떤 월간 리포트의 통계인지 식별한다. | 사진 분위기 통계와 월간 리포트를 연결한다. |
| `scene_tag` | `VARCHAR` | 50 (임시) | NOT NULL | - | - | 사진 분석을 통해 분류된 분위기 또는 장면 태그 | `카페`, `노을`, `밤거리`, `자연` 등 문자열 형태의 분류값을 저장하므로 `VARCHAR`를 사용한다. 실제 태그 종류와 최대 길이는 AI 분석 정책 확정 후 결정한다. | 어떤 사진 분위기 또는 장면에 대한 통계인지 구분한다. |
| `count` | `INTEGER` | - | NOT NULL | 0 | - | 해당 장면 태그로 분류된 사진 개수 | 사진 개수는 정수값이므로 `INTEGER`를 사용한다. | 특정 장면이나 분위기가 한 달 동안 몇 번 나타났는지 표시한다. |
| `ratio` | `TINYINT UNSIGNED` | - | NOT NULL | 0 | - | 해당 장면 태그가 차지하는 비율 | 비율을 소수점 없이 0~100의 정수 퍼센트로 저장하므로 `TINYINT UNSIGNED`를 사용하고, `CHECK` 제약으로 실제 허용 범위를 0~100으로 제한한다. | 월간 리포트에서 사진 분위기 비율과 그래프를 표시하는 데 사용한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조/조건 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_monthly_photo_scene_stats` | PRIMARY KEY | `photo_scene_stat_id` | - | 각 월간 사진 분위기 통계 데이터를 고유하게 식별한다. |
| `fk_monthly_photo_scene_stats_report` | FOREIGN KEY | `monthly_report_id` | `monthly_reports.monthly_report_id` | 사진 분위기 통계를 해당 월간 리포트와 연결하며, 존재하지 않는 월간 리포트에 통계가 저장되지 않도록 한다. |
| `uk_monthly_photo_scene_stats_report_tag` | UNIQUE | `monthly_report_id`, `scene_tag` | - | 하나의 월간 리포트에 동일한 장면 태그 통계가 중복 저장되지 않도록 한다. |
| `chk_monthly_photo_scene_stats_ratio` | CHECK | `ratio` | `ratio BETWEEN 0 AND 100` | 사진 분위기 비율이 유효한 백분율 범위인 0~100의 정수 값만 가지도록 제한한다. |
| chk_monthly_photo_scene_stats_count | CHECK | count |  | 사진 개수는 음수가 될 수 없으므로 `CHECK (count >= 0)`를 적용한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `monthly_reports` | `monthly_photo_scene_stats.monthly_report_id` ↔ `monthly_reports.monthly_report_id` | N:1 | 하나의 사진 분위기 통계는 하나의 월간 리포트에 속하며, 하나의 월간 리포트에는 여러 사진 분위기 또는 장면별 통계가 존재할 수 있다. |

---

## 14. recommendation_playlists - 추천 플레이리스트 테이블

#### 1. 테이블 설명

사용자에게 생성된 현재 추천 플레이리스트 정보를 저장하는 테이블이다. 사용자가 서비스를 나갔다가 다시 접속해도 마지막으로 생성된 추천 플레이리스트를 유지하며, 새 추천 요청이 발생하면 기존 추천 플레이리스트를 삭제하고 새로운 추천 플레이리스트를 생성한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `recommendation_playlist_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 추천 플레이리스트 고유 ID | 각 추천 플레이리스트를 고유하게 식별하고 데이터 증가에 따른 ID 범위 확장성을 고려하여 `BIGINT`를 사용한다. | 추천 플레이리스트와 해당 플레이리스트의 음악 목록을 식별하는 기준이 된다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK, UK | 추천 플레이리스트 소유 사용자 ID | `users.user_id`를 참조한다. 현재 정책상 사용자별 현재 추천 플레이리스트 하나만 유지하므로 UNIQUE를 적용한다. | 어떤 사용자의 추천 플레이리스트인지 식별한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 추천 플레이리스트가 최초 생성된 시각 | 최초 추천 플레이리스트가 만들어진 시점을 기록한다. | 사용자의 추천 플레이리스트 최초 생성 시점을 관리한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_recommendation_playlists` | PRIMARY KEY | `recommendation_playlist_id` | - | 각 추천 플레이리스트를 고유하게 식별한다. |
| `fk_recommendation_playlists_user` | FOREIGN KEY | `user_id` | `users.user_id` | 추천 플레이리스트를 해당 사용자와 연결한다. |
| `uk_recommendation_playlists_user` | UNIQUE | `user_id` | - | 한 사용자에게 현재 추천 플레이리스트가 하나만 존재하도록 한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `recommendation_playlists.user_id` ↔ `users.user_id` | 0..1:1 | 하나의 추천 플레이리스트는 한 사용자에게 속한다. 현재 정책상 한 사용자에게 하나의 현재 추천 플레이리스트만 유지한다. |
| `recommendation_playlist_items` | `recommendation_playlists.recommendation_playlist_id` ↔ `recommendation_playlist_items.recommendation_playlist_id` | 1:N | 하나의 추천 플레이리스트에는 여러 개의 추천 음악이 포함된다. |

#### 5. 비즈니스 규칙

- 추천 생성 시 날씨·기온·시간 등의 Context를 사용할 수 있으나, 현재 추천 플레이리스트 리소스에는 해당 값을 저장하지 않는다.
- 현재 추천 플레이리스트 조회 응답에도 생성 당시의 `weatherCondition`, `temperature`, `time` 값을 포함하지 않는다.

---

## 15. recommendation_playlist_items - 추천 플레이리스트 음악 테이블

#### 1. 테이블 설명

추천 플레이리스트에 포함된 음악 목록과 순서를 저장하는 테이블이다. 하나의 추천 플레이리스트에 여러 음악을 연결하여 사용자가 다시 접속했을 때 마지막으로 생성된 추천 음악 목록을 동일하게 제공하는 데 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `recommendation_playlist_item_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 추천 플레이리스트 음악 고유 ID | 플레이리스트에 포함된 각각의 음악 행을 고유하게 식별한다. | 하나의 추천 음악 항목을 식별한다. |
| `recommendation_playlist_id` | `BIGINT` | - | NOT NULL | - | FK | 음악이 포함된 추천 플레이리스트 ID | `recommendation_playlists.recommendation_playlist_id`를 참조한다. | 어떤 추천 플레이리스트에 포함된 음악인지 연결한다. |
| `music_track_id` | `BIGINT` | - | NOT NULL | - | FK | 추천된 음악 ID | `music_tracks.music_track_id`를 참조하여 기존 음악 정보와 연결한다. | 추천된 음악의 제목·아티스트·앨범 등의 정보를 조회하는 데 사용한다. |
| `music_order` | `INTEGER` | - | NOT NULL | - | - | 추천 플레이리스트 내 음악 순서 | 플레이리스트의 곡 순서를 정수로 저장한다. | AI가 추천한 음악을 정해진 순서대로 표시한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_recommendation_playlist_items` | PRIMARY KEY | `recommendation_playlist_item_id` | - | 각 추천 플레이리스트 음악 항목을 고유하게 식별한다. |
| `fk_recommendation_playlist_items_playlist` | FOREIGN KEY | `recommendation_playlist_id` | `recommendation_playlists.recommendation_playlist_id` | 음악 항목을 해당 추천 플레이리스트와 연결하며, 추천 플레이리스트 삭제 시 해당 플레이리스트의 음악 항목도 함께 삭제되도록 `ON DELETE CASCADE`를 적용한다. |
| `fk_recommendation_playlist_items_music` | FOREIGN KEY | `music_track_id` | `music_tracks.music_track_id` | 추천 항목을 실제 음악 정보와 연결한다. |
| `uk_recommendation_playlist_items_rank` | UNIQUE | `recommendation_playlist_id`, `music_order` | - | 하나의 플레이리스트 안에서 동일한 순번이 중복되지 않도록 한다. |
| chk_recommendation_playlist_items_music_order | CHECK | `music_order` | - | 추천 순번이 1 이상의 값만 가지도록 `CHECK (music_order > 0)`를 적용한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `recommendation_playlists` | `recommendation_playlist_items.recommendation_playlist_id` ↔ `recommendation_playlists.recommendation_playlist_id` | N:1 | 하나의 음악 항목은 하나의 추천 플레이리스트에 속하며, 하나의 추천 플레이리스트에는 여러 음악 항목이 존재한다. |
| `music_tracks` | `recommendation_playlist_items.music_track_id` ↔ `music_tracks.music_track_id` | N:1 | 하나의 추천 항목은 하나의 음악을 참조하며, 동일한 음악이 여러 사용자의 추천 플레이리스트에 포함될 수 있다. |

---

## 16. vote_questions - 취향 투표 질문 테이블

#### 1. 테이블 설명

사용자에게 제공할 장르별 취향 투표 질문과 선택지를 저장하는 테이블이다. 사용자의 `users.music_genre`와 동일한 `music_genre`의 당일 질문을 제공하며, 같은 장르의 같은 질문에서 선택한 답변을 이후 매칭 기준으로 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `vote_question_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 취향 투표 질문 고유 ID | 각 투표 질문을 고유하게 식별하기 위해 사용한다. 데이터 증가 가능성을 고려하여 `BIGINT`를 사용한다. | `vote_answers`, `match_requests` 등에서 어떤 투표 질문에 대한 데이터인지 식별하는 기준이 된다. |
| `question_text` | `VARCHAR` | 100 | NOT NULL | - | - | 사용자에게 보여줄 취향 투표 질문 내용 | 질문 문장은 최대 100자가 넘지 않도록 한다. | 취향 투표 화면에 질문을 표시한다. |
| `option_a` | `VARCHAR` | 100 | NOT NULL | - | - | 첫 번째 선택지 | 선택지는 짧은 문자열이므로 `VARCHAR`를 사용한다. | 사용자가 선택할 첫 번째 답변을 표시한다. |
| `option_b` | `VARCHAR` | 100 | NOT NULL | - | - | 두 번째 선택지 | 선택지는 짧은 문자열이므로 `VARCHAR`를 사용한다. | 사용자가 선택할 두 번째 답변을 표시한다. |
| `music_genre` | `VARCHAR` | 50 | NOT NULL | - | - | 질문이 속한 음악 장르 | 장르별로 질문을 구분하고 사용자의 `users.music_genre`와 동일한 장르의 질문을 조회하기 위해 문자열로 저장한다. | 사용자의 대표 음악 장르에 맞는 취향 투표 질문을 제공하고, 동일 장르 사용자 안에서 투표·매칭이 이루어지도록 하는 기준값으로 사용한다. |
| `vote_date` | `DATE` | - | NOT NULL | - | - | 해당 투표 질문이 사용자에게 제공되는 날짜 | 취향 투표는 하루 단위로 운영되므로 시간 범위 대신 날짜만 저장한다. | 현재 날짜와 사용자의 `music_genre`를 함께 기준으로 질문을 조회하여 장르별 하루 하나의 질문을 제공한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 투표 질문이 생성된 시각 | 해당 질문이 시스템에 등록된 시점을 기록하기 위해 사용한다. | 질문 생성 이력을 관리한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_vote_questions` | PRIMARY KEY | `vote_question_id` | - | 각 취향 투표 질문을 고유하게 식별한다. |
| `uk_vote_questions_genre_date` | UNIQUE | `music_genre`, `vote_date` | - | 동일한 장르와 날짜 조합에 둘 이상의 질문이 등록되지 않도록 하여 장르별 하루 하나의 질문만 존재하도록 한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_vote_question_by_genre` | 사용자는 자신의 `users.music_genre`와 동일한 `vote_questions.music_genre`의 당일 질문을 제공받는다. |
| `rule_matching_within_genre_question` | 사용자의 장르는 질문에 귀속되어 있으므로 매칭 시 별도 장르 컬럼을 중복 저장하지 않고 `vote_answer_id → vote_question_id → music_genre` 경로로 동일 장르·동일 질문 여부를 확인할 수 있다. |
| `rule_vote_question_genre_required` | 대표 음악 장르가 결정된 사용자만 자신의 `users.music_genre`와 동일한 장르의 당일 질문에 참여할 수 있다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `vote_answers` | `vote_questions.vote_question_id` ↔ `vote_answers.vote_question_id` | 1:N | 하나의 취향 투표 질문에 여러 사용자의 답변이 존재할 수 있다. |
| `chat_rooms` | `vote_questions.vote_question_id` ↔ `chat_rooms.vote_question_id` | 1:N | 하나의 취향 투표 질문을 기준으로 여러 매칭 채팅방이 생성될 수 있다. |

---

## 17. vote_answers - 취향 투표 답변 테이블

#### 1. 테이블 설명

사용자가 취향 투표 질문에서 선택한 답변을 저장하는 테이블이다. 어떤 사용자가 어떤 질문에서 어떤 선택지를 선택했는지 기록하며, 이후 동일 질문에서 A 선택 2명과 B 선택 2명을 구성하는 매칭의 기준 데이터로 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `vote_answer_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 취향 투표 답변 고유 ID | 각각의 투표 답변을 고유하게 식별하기 위해 사용하며 데이터 증가 가능성을 고려해 `BIGINT`를 사용한다. | 개별 투표 답변을 식별하고 매칭 등의 다른 데이터에서 특정 답변을 참조할 수 있게 한다. |
| `vote_question_id` | `BIGINT` | - | NOT NULL | - | FK | 사용자가 답변한 취향 투표 질문 ID | `vote_questions.vote_question_id`를 참조한다. | 어떤 질문에 대한 답변인지 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 취향 투표에 참여한 사용자 ID | `users.user_id`를 참조한다. | 누가 해당 답변을 선택했는지 식별한다. |
| `selected_option` | `VARCHAR` | 100 | NOT NULL | - | - | 사용자가 선택한 선택지 | 두 개의 선택지 중 어떤 것을 선택했는지 실제 선택지 텍스트로 저장한다. | 동일 질문의 매칭 대상에서 A/B 선택 인원을 판별하여 A 2명 + B 2명 구성을 만드는 데 사용한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 사용자가 투표한 시각 | 사용자가 실제로 답변을 제출한 시점을 기록한다. | 투표 참여 시점을 확인하고 답변 이력을 관리한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_vote_answers` | PRIMARY KEY | `vote_answer_id` | - | 각 취향 투표 답변을 고유하게 식별한다. |
| `fk_vote_answers_question` | FOREIGN KEY | `vote_question_id` | `vote_questions.vote_question_id` | 존재하는 취향 투표 질문에 대해서만 답변을 저장할 수 있도록 한다. |
| `fk_vote_answers_user` | FOREIGN KEY | `user_id` | `users.user_id` | 존재하는 사용자의 투표 답변만 저장할 수 있도록 한다. |
| `uk_vote_answers_user_question` | UNIQUE | `user_id`, `vote_question_id` | - | 한 사용자가 동일한 취향 투표 질문에 두 번 이상 답변하는 것을 방지한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `vote_answers.user_id` → `users.user_id` | N:1 | 한 사용자는 여러 취향 투표 질문에 답변할 수 있으며, 각 투표 답변은 한 명의 사용자에게 속한다. |
| `vote_questions` | `vote_answers.vote_question_id` → `vote_questions.vote_question_id` | N:1 | 하나의 취향 투표 질문에는 여러 사용자의 답변이 존재할 수 있으며, 각 투표 답변은 하나의 질문에 속한다. |

---

## 18. match_requests - 매칭 요청 테이블

#### 1. 테이블 설명

사용자가 취향 투표 답변을 기준으로 랜덤 채팅 매칭을 요청한 정보를 저장하는 테이블이다.
매칭 요청의 대기, 완료, 만료 상태를 관리한다. 동일 질문에서 A 선택 사용자 2명과 B 선택 사용자 2명이 23:00 이전에 모이면 매칭이 완료되어 생성된 채팅방을 해당 요청과 연결한다. 23:00 시점까지 매칭되지 않은 요청은 `EXPIRED`로 변경한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `match_request_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 매칭 요청 고유 ID | 각 매칭 요청을 고유하게 식별하고 데이터 증가 가능성을 고려하여 `BIGINT`를 사용한다. | 개별 매칭 요청을 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 매칭을 요청한 사용자 ID | `users.user_id`를 참조하여 요청자를 식별한다. | 누가 매칭을 요청했는지 확인한다. |
| `vote_answer_id` | `BIGINT` | - | NOT NULL | - | FK | 매칭 기준이 되는 취향 투표 답변 ID | `vote_answers.vote_answer_id`를 참조하여 사용자가 어떤 질문에서 어떤 답변을 선택했는지 연결한다. | 동일한 `vote_question_id`를 기준으로 A 선택 2명과 B 선택 2명을 구성하는 매칭에 사용한다. |
| `status` | `ENUM('WAITING', 'MATCHED', 'EXPIRED')` | - | NOT NULL | `WAITING` | - | 매칭 요청 상태
( `WAITING`, `MATCHED`, `EXPIRED`) | 매칭 요청 상태가 `WAITING`, `MATCHED`, `EXPIRED` 세 가지로 확정되어 있으므로 허용 가능한 상태값을 제한하기 위해 `ENUM`을 사용한다. | 매칭 요청이 대기 중인지, 매칭 완료되어 채팅방에 배정되었는지, 정책상 만료된 상태인지를 구분한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 매칭 요청 생성 시각 | 실제 매칭 요청이 발생한 시점을 기록한다. | 매칭 요청 시점 기록, 대기 순서 확인 및 일자별 요청 조회 등에 활용한다. |
| `chat_room_id` | `BIGINT` | - | NULL | NULL | FK | `MATCHED` 상태가 되었을 때 배정된 채팅방 ID
 | `chat_rooms.chat_room_id`를 참조한다. 매칭 대기 중이거나 만료된 요청에는 채팅방이 존재하지 않으므로 `NULL`을 허용한다.
 | 해당 매칭 요청의 결과로 사용자가 어느 채팅방에 배정되었는지 확인한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_match_requests` | PRIMARY KEY | `match_request_id` | - | 각 매칭 요청을 고유하게 식별한다. |
| `fk_match_requests_user` | FOREIGN KEY | `user_id` | `users.user_id` | 존재하는 사용자만 매칭을 요청할 수 있도록 한다. |
| `fk_match_requests_vote_answer` | FOREIGN KEY | `vote_answer_id` | `vote_answers.vote_answer_id` | 존재하는 취향 투표 답변을 기준으로만 매칭을 요청할 수 있도록 한다. |
| `fk_match_requests_chat_room` | FOREIGN KEY | `chat_room_id` | `chat_rooms.chat_room_id` | 채팅방에 배정된 경우 실제 존재하는 채팅방만 참조하도록 하며, 채팅방 삭제 시 해당 채팅방에 연결된 매칭 요청도 함께 삭제되도록 `ON DELETE CASCADE`를 적용한다. |
| `uk_match_requests_vote_answer` | UNIQUE | `vote_answer_id` | - | 하나의 취향 투표 답변으로 매칭 요청이 중복 생성되는 것을 방지한다. 하루 하나의 질문에 대해 사용자당 하나의 답변만 생성되는 정책과 함께 사용되어, 사용자가 해당 일자의 매칭 요청을 한 번만 생성하도록 제한한다. |
| `chk_match_requests_room_by_status`  | CHECK | `status` , `chat_room_id` | - | `MATCHED` 상태에서는 `chat_room_id`가 반드시 존재하고, `WAITING` 또는 `EXPIRED` 상태에서는 `chat_room_id`가 `NULL`이 되도록 하여 매칭 상태와 채팅방 배정 정보의 일관성을 보장한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_match_complete` | 동일한 `vote_question_id`의 `WAITING` 요청 중 A 선택 사용자 2명과 B 선택 사용자 2명이 23:00 이전에 모이면 4인 채팅방을 생성하고 해당 4개 요청을 `MATCHED`로 변경한다. |
| `rule_match_request_cutoff` | 당일 23:00 이후에는 새로운 매칭 요청을 생성하지 않는다. |
| `rule_match_expire` | 당일 23:00 시점에 아직 `WAITING` 상태인 매칭 요청은 모두 `EXPIRED`로 변경한다. 23:00 이후에는 기존 `WAITING` 요청을 대상으로 추가 매칭을 진행하지 않는다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `match_requests.user_id` → `users.user_id` | N:1 | 한 사용자는 날짜별로 여러 매칭 요청을 가질 수 있으며 각 매칭 요청은 한 사용자에게 속한다. |
| `vote_answers` | `match_requests.vote_answer_id` → `vote_answers.vote_answer_id` | 1:0..1 | 하나의 투표 답변으로 최대 한 번의 매칭 요청을 할 수 있으며, 매칭 요청은 하나의 투표 답변을 기준으로 한다. |
| `chat_rooms` | `match_requests.chat_room_id` → `chat_rooms.chat_room_id` | N:0..1 | 여러 매칭 요청이 하나의 채팅방에 배정될 수 있으며, 각 매칭 요청은 최대 하나의 채팅방에 배정된다. |

---

## 19. chat_rooms - 채팅방 테이블

#### 1. 테이블 설명

동일한 `vote_question_id`의 매칭 요청 중 A 선택 사용자 2명과 B 선택 사용자 2명이 모였을 때 생성되는 4인 채팅방을 저장하는 테이블이다. 채팅방 생성 시 매칭된 4명의 사용자가 배정되며, 이후 채팅 참여자와 메시지를 관리하는 기준이 된다. 채팅방은 생성 당일에만 유지되며, 자정이 되거나 참여자 4명이 모두 퇴장하면 삭제된다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `chat_room_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 채팅방 고유 ID | 각 채팅방을 고유하게 식별하고 데이터 증가 가능성을 고려하여 `BIGINT`를 사용한다. | 채팅 참여자와 메시지가 특정 채팅방을 참조할 때 기준 ID로 사용한다. |
| `vote_question_id` | `BIGINT` | - | NOT NULL | - | FK | 채팅방 매칭의 기준이 된 취향 투표 질문 ID | 동일한 질문에서 A 선택 사용자 2명과 B 선택 사용자 2명을 한 채팅방으로 구성하므로 `vote_questions.vote_question_id`를 참조한다. | 어떤 취향 투표 질문을 기준으로 생성된 채팅방인지 식별한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 채팅방 생성 시각 | 4명의 사용자가 모여 채팅방이 실제 생성된 시점을 기록한다. | 채팅방 생성 시점을 확인한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_chat_rooms` | PRIMARY KEY | `chat_room_id` | - | 각 채팅방을 고유하게 식별한다. |
| `fk_chat_rooms_vote_question` | FOREIGN KEY | `vote_question_id` | `vote_questions.vote_question_id` | 존재하는 취향 투표 질문을 기준으로 생성된 채팅방만 저장하도록 한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_chat_room_match_composition` | 하나의 채팅방은 동일한 `vote_question_id`에서 A 선택 사용자 2명과 B 선택 사용자 2명으로 구성한다. 선택지는 참여자의 `vote_answers.selected_option`에서 판별하며 `chat_rooms`에 단일 선택지 컬럼을 중복 저장하지 않는다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `vote_questions` | `chat_rooms.vote_question_id` → `vote_questions.vote_question_id` | N:1 | 하나의 취향 투표 질문을 기준으로 여러 개의 채팅방이 생성될 수 있으며, 각 채팅방은 하나의 질문을 기준으로 한다. |
| `chat_room_participants` | `chat_room_participants.chat_room_id` → `chat_rooms.chat_room_id` | 1:N | 하나의 채팅방에는 매칭이 완료된 4명의 참여자 정보가 생성되며, 각 참여자는 하나의 채팅방에 속한다. |
| `chat_messages` | `chat_messages.chat_room_id` → `chat_rooms.chat_room_id` | 1:N | 하나의 채팅방에는 여러 개의 메시지가 존재할 수 있으며, 각 메시지는 하나의 채팅방에 속한다. |

---

## 20. chat_room_participants - 채팅방 참여자 테이블

#### 1. 테이블 설명

사용자가 어떤 채팅방에 참여했는지 저장하는 테이블이다. 채팅방과 사용자 간 참여 관계를 관리하며, 사용자의 입장 시점과 퇴장 시점을 기록한다. 한 사용자는 같은 채팅방에 한 번만 참여할 수 있고, 퇴장 후 재입장은 허용하지 않는다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `chat_room_participant_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 채팅방 참여 정보 고유 ID | 각 참여 정보를 고유하게 식별하고 데이터 증가 가능성을 고려하여 `BIGINT`를 사용한다. | 개별 채팅방 참여 정보를 식별한다. |
| `chat_room_id` | `BIGINT` | - | NOT NULL | - | FK | 사용자가 참여한 채팅방 ID | `chat_rooms.chat_room_id`를 참조한다. | 어떤 채팅방에 참여했는지 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 채팅방에 참여한 사용자 ID | `users.user_id`를 참조한다. | 어떤 사용자가 채팅방에 참여했는지 식별한다. |
| `joined_at` | `DATETIME` | - | NULL | NULL | - | 채팅방 최초 입장 시각 | 매칭 완료 시 참여자 정보가 먼저 생성되고 사용자가 실제 채팅방에 입장하기 전에는 입장 시각이 없으므로 `NULL`을 허용한다. | 사용자가 실제로 채팅방에 최초 입장한 시점을 기록한다. |
| `left_at` | `DATETIME` | - | NULL | NULL | - | 채팅방 퇴장 시각 | 사용자가 아직 채팅방을 나가지 않은 경우 값이 존재하지 않으므로 `NULL`을 허용한다. | `NULL`이면 아직 퇴장하지 않은 상태이고, 값이 있으면 해당 시점에 채팅방을 나간 것으로 판단한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_chat_room_participants` | PRIMARY KEY | `chat_room_participant_id` | - | 각 채팅방 참여 정보를 고유하게 식별한다. |
| `fk_chat_room_participants_room` | FOREIGN KEY | `chat_room_id` | `chat_rooms.chat_room_id` | 존재하는 채팅방에 대해서만 참여 정보를 저장할 수 있도록 하며, 채팅방 삭제 시 해당 채팅방의 참여자 정보도 함께 삭제되도록 `ON DELETE CASCADE`를 적용한다. |
| `fk_chat_room_participants_user` | FOREIGN KEY | `user_id` | `users.user_id` | 존재하는 사용자에 대해서만 채팅방 참여 정보를 저장할 수 있도록 한다. |
| `uk_chat_room_participants_room_user` | UNIQUE | `chat_room_id`, `user_id` | - | 동일한 사용자가 같은 채팅방에 중복으로 참여자 등록되는 것을 방지한다. 퇴장 후 재입장 여부는 `left_at`을 기준으로 애플리케이션 로직에서 판단한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_chat_room_join` | 매칭 완료 시 4명의 참여자 정보를 먼저 생성하고, 사용자가 실제 채팅방에 입장하면 `joined_at`을 기록한다. |
| `rule_chat_room_reentry` | `left_at`이 존재하는 사용자는 해당 채팅방에 다시 입장할 수 없다. |
| `rule_chat_room_delete` | 채팅방 참여자 4명이 모두 퇴장했거나 자정이 되면 채팅방을 삭제한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `chat_rooms` | `chat_room_participants.chat_room_id` → `chat_rooms.chat_room_id` | N:1 | 하나의 채팅방에는 매칭이 완료된 4명의 참여자 정보가 생성되며, 각 참여 정보는 하나의 채팅방에 속한다. 채팅방이 삭제되면 해당 참여자 정보도 함께 삭제된다. |
| `users` | `chat_room_participants.user_id` → `users.user_id` | N:1 | 한 사용자는 서로 다른 여러 채팅방에 참여할 수 있으며, 각 참여 정보는 한 명의 사용자에게 속한다. |

---

## 21. chat_messages - 채팅 메시지 테이블

#### 1. 테이블 설명

채팅방에서 사용자가 전송한 메시지를 저장하는 테이블이다. 어떤 채팅방에서 어떤 사용자가 어떤 메시지를 언제 전송했는지 기록하고, 해당 사용자가 실제 채팅방 참여자인지는 애플리케이션 로직에서 검증한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `chat_message_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 채팅 메시지 고유 ID | 각 메시지를 고유하게 식별하고 메시지가 지속적으로 누적되는 특성을 고려하여 `BIGINT`를 사용한다. | 개별 채팅 메시지를 식별한다. |
| `chat_room_id` | `BIGINT` | - | NOT NULL | - | FK | 메시지가 전송된 채팅방 ID | `chat_rooms.chat_room_id`를 참조하여 메시지가 어느 채팅방에 속하는지 연결한다. | 특정 채팅방의 메시지 내역을 조회하는 데 사용한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 메시지를 전송한 사용자 ID | `users.user_id`를 참조하여 메시지 작성자를 식별한다. | 메시지를 누가 전송했는지 확인한다. |
| `content` | `VARCHAR` | 500 | NOT NULL | - | - | 메시지 내용 | 일반 텍스트 채팅 메시지를 저장하며, UI 정책상 메시지 최대 길이를 500자로 제한하므로 `VARCHAR(500)`을 사용한다. | 사용자가 채팅방에서 전송한 실제 메시지 내용을 저장한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 메시지 전송 시각 | 메시지가 생성된 시점을 기록한다. | 메시지의 전송 시각을 표시하고 메시지 순서를 조회하는 데 활용한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_chat_messages` | PRIMARY KEY | `chat_message_id` | - | 각 채팅 메시지를 고유하게 식별한다. |
| `fk_chat_messages_room` | FOREIGN KEY | `chat_room_id` | `chat_rooms.chat_room_id` | 메시지가 실제 존재하는 채팅방에 속하도록 참조 관계를 보장하며, 채팅방 삭제 시 해당 채팅방의 메시지도 함께 삭제되도록 `ON DELETE CASCADE`를 적용한다. |
| `fk_chat_messages_user` | FOREIGN KEY | `user_id` | `users.user_id` | 메시지의 발신자가 실제 존재하는 사용자인지 보장한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_chat_message_sender` | 메시지 전송 시 사용자가 해당 채팅방의 참여자인지 확인하고, `joined_at`이 존재하며 `left_at`이 `NULL`인 경우에만 메시지 전송을 허용한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `chat_rooms` | `chat_messages.chat_room_id` → `chat_rooms.chat_room_id` | N:1 | 하나의 채팅방에는 여러 메시지가 존재할 수 있으며, 각 메시지는 하나의 채팅방에 속한다. |
| `users` | `chat_messages.user_id` → `users.user_id` | N:1 | 한 사용자는 여러 메시지를 전송할 수 있으며, 각 메시지는 한 명의 발신자에 의해 작성된다. |

---

## 22. notifications - 알림 테이블

#### 1. 테이블 설명

사용자에게 전달할 서비스 알림 정보를 저장하는 테이블이다. 알림의 대상 사용자, 알림 유형, 내용, 발생 시점과 읽음 여부를 관리하며 사용자가 자신의 알림 목록을 조회할 때 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | 길이 | NULL 여부 | 기본값 | Key | 설명 | 타입/설계 근거 | 기능적 역할 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `notification_id` | `BIGINT` | - | NOT NULL | `AUTO_INCREMENT` | PK | 알림 고유 ID | 각 알림을 고유하게 식별하기 위해 사용한다. | 개별 알림을 식별한다. |
| `user_id` | `BIGINT` | - | NOT NULL | - | FK | 알림 수신 사용자 ID | `users.user_id`를 참조한다. | 누구에게 전달되는 알림인지 식별한다. |
| `type` | `ENUM` | - | NOT NULL | - | - | 알림 유형
`MONTHLY_REPORT_CREATED`, 
`AI_PLAYLIST`, 
`MATCH_COMPLETED`, 
`FRIEND_REQUEST`, 
`FRIEND_ACCEPTED` | 발생 원인과 알림 클릭 시 처리할 동작을 구분하기 위해 사용한다. | 월별 리포트, AI 플리, 매칭 완료, 친구 요청/수락 알림을 구분한다. |
| `monthly_report_id` | `BIGINT` | - | NULL | NULL | FK | 생성된 월별 리포트 ID | `MONTHLY_REPORT_CREATED` 알림일 때만 사용하므로 `NULL`을 허용한다. | 알림 클릭 시 해당 월별 리포트와 연결한다. |
| `recommendation_playlist_id` | `BIGINT` | - | NULL | NULL | FK | 생성된 AI 추천 플레이리스트 ID | `AI_PLAYLIST` 알림일 때만 사용하므로 `NULL`을 허용한다. | 알림 클릭 시 생성된 AI 플레이리스트와 연결한다. |
| `chat_room_id` | `BIGINT` | - | NULL | NULL | FK | 매칭 완료로 생성된 채팅방 ID | `MATCH_COMPLETED` 알림일 때만 사용하므로 `NULL`을 허용한다. | 알림 클릭 시 매칭된 채팅방으로 이동하는 데 사용한다. |
| `friendship_id` | `BIGINT` | - | NULL | NULL | FK | 친구 관계 ID | `FRIEND_ACCEPTED` 알림에서 사용하며 `friendships.friendship_id`를 참조한다. | 수락 결과 생성된 친구 관계를 식별한다. |
| `friend_request_id` | `BIGINT` | - | NULL | NULL | FK | 친구 요청 ID | 친구 요청 알림에서 사용하며 `friend_requests.friend_request_id`를 참조한다. | 어떤 친구 요청으로 발생한 알림인지 식별하고, 수락·거절 처리 시 해당 친구 요청을 참조하는 데 사용한다. |
| `title` | `VARCHAR` | 100 | NOT NULL | - | - | 알림 제목 | 알림 목록에서 주요 내용을 간단히 보여준다. | 사용자에게 알림 종류와 핵심 내용을 표시한다. |
| `content` | `VARCHAR` | 100 | NOT NULL | - | - | 알림 내용 | 사용자에게 전달할 구체적인 알림 문구를 저장한다. | 알림 상세 내용을 표시한다. |
| `read_at` | `DATETIME` | - | NULL | NULL | - | 알림을 읽은 시각 | 읽기 전에는 값이 없으므로 `NULL`을 허용한다. | `NULL`이면 읽지 않은 알림, 값이 있으면 읽은 알림으로 판단한다. |
| `created_at` | `DATETIME` | - | NOT NULL | `CURRENT_TIMESTAMP` | - | 알림 생성 시각 | 알림이 발생한 시점을 기록한다. | 알림 정렬 및 발생 시각 표시에 사용한다. |

#### 3. 제약조건

| 제약조건명 | 유형 | 컬럼 | 참조 | 설명 |
| --- | --- | --- | --- | --- |
| `pk_notifications` | PRIMARY KEY | `notification_id` | - | 각 알림을 고유하게 식별한다. |
| `fk_notifications_user` | FOREIGN KEY | `user_id` | `users.user_id` | 알림 수신자가 실제 사용자 데이터를 참조하도록 한다. |
| `fk_notifications_monthly_report` | FOREIGN KEY, UNIQUE | `monthly_report_id` | `monthly_reports.monthly_report_id` | `MONTHLY_REPORT_CREATED` 알림이 실제 생성된 리포트를 참조하도록 하며, 리포트가 삭제되면 알림은 유지하고 `monthly_report_id`만 `NULL`이 되도록 `ON DELETE SET NULL`을 적용한다. |
| `fk_notifications_playlist` | FOREIGN KEY, UNIQUE | `recommendation_playlist_id` | `recommendation_playlists.recommendation_playlist_id` | `AI_PLAYLIST` 알림이 실제 생성된 플레이리스트를 참조하도록 하며, 플레이리스트가 삭제되면 알림은 유지하고 `recommendation_playlist_id`만 `NULL`이 되도록 `ON DELETE SET NULL`을 적용한다. |
| `fk_notifications_chat_room` | FOREIGN KEY | `chat_room_id` | `chat_rooms.chat_room_id` | `MATCH_COMPLETED` 알림이 실제 생성된 채팅방을 참조하도록 하며, 채팅방이 삭제되면 알림은 유지하고 `chat_room_id`만 `NULL`이 되도록 `ON DELETE SET NULL`을 적용한다. |
| `fk_notifications_friendship` | FOREIGN KEY, UNIQUE | `friendship_id` | `friendships.friendship_id` | `FRIEND_ACCEPTED` 알림이 실제로 생성된 친구 관계를 참조하도록 하며, 친구 관계가 삭제되면 알림은 유지하고 `friendship_id`만 `NULL`이 되도록 `ON DELETE SET NULL`을 적용한다. |
| `chk_notifications_type` | CHECK | `type` | - | 알림 유형을 `MONTHLY_REPORT_CREATED`, `AI_PLAYLIST`, `MATCH_COMPLETED`, `FRIEND_REQUEST`, `FRIEND_ACCEPTED`로 제한한다. |
| `fk_notifications_friend_request` | FOREIGN KEY, UNIQUE | `friend_request_id` | `friend_requests.friend_request_id` | `FRIEND_REQUEST` 알림이 실제 친구 요청 데이터를 참조하도록 하며, 친구 요청이 삭제되면 알림은 유지하고 `friend_request_id`만 `NULL`이 되도록 `ON DELETE SET NULL`을 적용한다. |

#### 3-1. 비즈니스 규칙

| 규칙명 | 설명 |
| --- | --- |
| `rule_notification_target_delete` | 알림이 참조하는 Target Resource가 삭제되어도 알림 자체는 과거 이력으로 유지한다. 해당 Target FK만 `NULL`로 변경한다. |
| `rule_notification_target_action` | Target FK가 `NULL`인 과거 알림은 화면에 표시할 수 있으나 해당 리소스로 이동하는 액션은 비활성화한다. |

#### 4. 관계

| 관련 테이블 | 연결 컬럼 | 관계 | 관계 설명 |
| --- | --- | --- | --- |
| `users` | `notifications.user_id` → `users.user_id` | N:1 | 한 사용자는 여러 알림을 받을 수 있으며 각 알림은 한 명의 수신자에게 전달된다. |
| `monthly_reports` | `notifications.monthly_report_id` → `monthly_reports.monthly_report_id` | 0..1:1 | 월별 리포트 생성 알림인 경우 해당 리포트와 연결된다. 리포트 삭제 후에는 FK가 `NULL`이 되고 알림 자체는 유지된다. |
| `recommendation_playlists` | `notifications.recommendation_playlist_id` → `recommendation_playlists.recommendation_playlist_id` | 0..1:1 | AI 플레이리스트 생성 알림인 경우 생성된 플레이리스트와 연결된다. 플레이리스트 삭제 후에는 FK가 `NULL`이 되고 알림 자체는 유지된다. |
| `chat_rooms` | `notifications.chat_room_id` → `chat_rooms.chat_room_id` | N:0..1 | 매칭 완료 알림인 경우 생성된 채팅방과 연결된다. 채팅방 삭제 후에는 FK가 `NULL`이 되고 알림 자체는 유지된다. |
| `friend_requests` | `notifications.friend_request_id` → `friend_requests.friend_request_id` | 0..1:1 | 하나의 친구 요청에는 하나의 `FRIEND_REQUEST` 알림이 연결될 수 있다. 친구 요청 삭제 후에는 FK가 `NULL`이 되고 알림 자체는 유지된다. |
| `friendships` | `notifications.friendship_id` → `friendships.friendship_id` | 0..1:1 | 하나의 친구 관계에는 하나의 `FRIEND_ACCEPTED` 알림이 연결될 수 있다. 친구 관계 삭제 후에는 FK가 `NULL`이 되고 알림 자체는 유지된다. |
---

## 구현·설계 정합성 표

| 대상 | 원본/목표 설계 | Flyway V1~V4 및 현재 코드 | 문서 처리 |
| --- | --- | --- | --- |
| `places` | 법정동 코드·이름 nullable 쌍, 좌표 기반 장소 | V4에서 `external_place_id`, `place_name` 제거, `legal_dong_code`, `legal_dong_name`, 좌표 UNIQUE, 법정동 쌍 CHECK 적용 | V4 실제 구조를 본문에 반영 |
| `uploads` | 임시 업로드는 1시간 유효하며 GCS 객체를 최종 사진으로 전환 | V1은 `image_url`, `mime_type`, `file_size`, `created_at`만 저장하고 별도 `expires_at`, `status`, `object_key`는 없음 | 실제 컬럼은 V1 기준, 유효시간·정리는 설계 규칙으로 유지 |
| `friend_requests` | 대기 요청만 저장하고 처리 후 삭제 | V1에 별도 `status` 컬럼 없이 사용자 쌍 UNIQUE로 구성 | 현재 구조와 정책이 일치하며 `status` 컬럼을 임의 추가하지 않음 |
| `recommendation_playlists` | 사용자별 현재 플레이리스트 하나 유지 | V1은 `user_id`, `created_at`만 저장하며 `source`, `status`는 없음 | 현재 구조를 유지하고 추천 문맥은 정책으로만 기록 |
| `record_drafts` | 브라우저 로컬 임시저장을 사용하여 V1 기능에서 제외 | Flyway V1에 테이블과 FK가 실제 존재 | `V1 제외·실제 테이블 존재`; 삭제 여부는 새 migration 승인 필요 |
| `weather` | 현재 날씨 조회는 기상청 격자 캐시 사용 | V2의 도시 단위 `weather`가 존재하지만 현재 JPA/서비스는 사용하지 않음 | `구현됨·정책 확인 필요` 레거시 테이블로 보존 |
| `weather_grids`, `weather_grid_forecasts` | 날짜·격자별 최초 성공 단기예보 묶음 캐시 | V3 및 현재 날씨 서비스에서 사용 | 구현됨 |
| `monthly_reports`, `monthly_mood_stats` | 월 종료 후 스냅샷 생성 및 목록/상세 조회 | Flyway V1과 현재 작업 트리에 엔티티·생성·목록 조회 구현, 상세 API는 미구현 | DB는 구현됨, API 구현 상태는 정책·OpenAPI에서 구분 |

## Open Questions

| ID | 내용 |
| --- | --- |
| `OQ-001` | 음악 장르의 정식 목록 |
| `OQ-021` | 일반 AI 플레이리스트 곡 수 |
| `OQ-022` | 알림 보관 기간 |
| `OQ-023` | 월간 리포트 배치 실행 시각 |
| `WTH-002` | 국내 기상청 격자 범위 밖 좌표의 HTTP 상태와 오류 코드 |

---

## 23. weather - 도시 단위 레거시 날씨 테이블

#### 1. 테이블 설명

초기 도시 단위 날씨 캐시를 저장하기 위해 Flyway V2에서 추가된 테이블이다. 현재 날씨 조회 구현은 `weather_grids`, `weather_grid_forecasts`를 사용하므로 이 테이블은 `구현됨·정책 확인 필요` 상태다. 삭제 또는 데이터 이전은 기존 migration 수정이 아니라 별도 승인된 신규 Flyway migration으로만 수행한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | NULL 여부 | 기본값 | Key | 설명 |
| --- | --- | --- | --- | --- | --- |
| `weather_id` | `BIGINT` | NOT NULL | `AUTO_INCREMENT` | PK | 레거시 날씨 행 식별자 |
| `city_name` | `VARCHAR(20)` | NOT NULL | - | UK | 도시명 |
| `weather_condition` | `VARCHAR(20)` | NOT NULL | - | - | 날씨 상태 문자열 |
| `temperature` | `DECIMAL(3,1)` | NOT NULL | - | - | 도시 단위 기온 |
| `fetched_at` | `DATETIME` | NOT NULL | - | - | 외부 날씨를 수신한 시각 |

#### 3. 제약조건

| 이름 | 유형 | 컬럼 | 설명 |
| --- | --- | --- | --- |
| `pk_weather` | PRIMARY KEY | `weather_id` | 레거시 날씨 행 식별 |
| `uk_weather_city_name` | UNIQUE | `city_name` | 도시당 한 행 유지 |

#### 3-1. 운영 상태

- 현재 `weather` 엔티티와 Repository는 존재하지 않는다.
- `GET /api/weather`는 이 테이블이 아니라 격자 캐시를 사용한다.
- 유지·데이터 이전·삭제 정책은 아직 확정되지 않았다.

---

## 24. weather_grids - 기상청 격자 테이블

#### 1. 테이블 설명

기상청 단기예보 조회에 사용하는 격자 좌표 `(grid_x, grid_y)`를 중복 없이 관리한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | NULL 여부 | 기본값 | Key | 설명 |
| --- | --- | --- | --- | --- | --- |
| `weather_grid_id` | `BIGINT` | NOT NULL | `AUTO_INCREMENT` | PK | 격자 내부 식별자 |
| `grid_x` | `SMALLINT` | NOT NULL | - | UK(복합) | 기상청 X 격자 |
| `grid_y` | `SMALLINT` | NOT NULL | - | UK(복합) | 기상청 Y 격자 |
| `created_at` | `DATETIME` | NOT NULL | `CURRENT_TIMESTAMP` | - | 최초 저장 시각 |

> `weather_grid_id`, `SMALLINT`, `created_at`은 동기화 설계서의 저장 개념을 v7 물리 스키마로 구체화한 명칭/타입이다.

#### 3. 제약조건

| 이름 | 유형 | 컬럼 | 설명 |
| --- | --- | --- | --- |
| `pk_weather_grids` | PRIMARY KEY | `weather_grid_id` | 격자 식별 |
| `uk_weather_grids_xy` | UNIQUE | `grid_x`, `grid_y` | 동일 격자 중복 방지 |

---

## 25. weather_grid_forecasts - 격자별 날씨 예보 캐시 테이블

#### 1. 테이블 설명

기상청 단기예보 `getVilageFcst` 결과를 KST 날짜·격자·예보시각별로 캐시한다. 동일 KST 날짜·격자의 최초 성공 예보 묶음을 유지하며, 같은 날 새 발표본으로 갱신하지 않는다.

예보시각은 **KST 기준 1시간 단위 시간대(hour bucket)** 로 사용한다. 요청 시각의 분이 `00~29`이면 현재 정각, `30~59`이면 다음 정각으로 반올림한다. 예를 들어 `15:10` 요청은 `15:00`, `15:50` 요청은 `16:00` 예보를 사용한다.

#### 2. 컬럼 정의

| 컬럼명 | 데이터 타입 | NULL 여부 | 기본값 | Key | 설명 |
| --- | --- | --- | --- | --- | --- |
| `weather_grid_forecast_id` | `BIGINT` | NOT NULL | `AUTO_INCREMENT` | PK | 예보 캐시 고유 ID |
| `weather_grid_id` | `BIGINT` | NOT NULL | - | FK, UK(복합) | `weather_grids.weather_grid_id` 참조 |
| `cache_date` | `DATE` | NOT NULL | - | UK(복합) | 최초 성공 예보 묶음을 구분하는 요청일 KST 날짜 |
| `forecast_at` | `DATETIME` | NOT NULL | - | UK(복합) | KST 기준 1시간 단위 예보 대상 시각. 요청 시각에 가장 가까운 정각으로 선택한다. 예: `15:50 → 16:00` |
| `temperature` | `DECIMAL(3,1)` | NOT NULL | - | - | 예보 기온 |
| `weather_condition` | `ENUM('CLEAR','CLOUDY','OVERCAST','RAIN','SNOW','RAIN_SNOW','SHOWER')` | NOT NULL | - | - | MULO 최종 날씨 상태 |
| `base_at` | `DATETIME` | NOT NULL | - | - | 기상청 발표 기준시각 |
| `fetched_at` | `DATETIME` | NOT NULL | - | - | MULO가 기상청 응답을 수신한 시각 |

> `cache_date`는 동일한 미래 `forecast_at`이 전날과 오늘의 캐시에 함께 있을 수 있음을 구분한다. `fetched_at`은 최초 예보 묶음을 수신한 시각이다.

#### 3. 제약조건

| 이름 | 유형 | 컬럼 | 참조/설명 |
| --- | --- | --- | --- |
| `pk_weather_grid_forecasts` | PRIMARY KEY | `weather_grid_forecast_id` | 예보 캐시 식별 |
| `fk_weather_grid_forecasts_grid` | FOREIGN KEY | `weather_grid_id` | `weather_grids.weather_grid_id` |
| `uk_weather_grid_forecasts_daily_slot` | UNIQUE | `weather_grid_id`, `cache_date`, `forecast_at` | 동일 날짜·격자·예보시각 중복 방지 |

#### 3-1. 예보시각 정규화 및 캐시 갱신 규칙

- 서비스 시간 기준은 `Asia/Seoul (KST)`이다.
- `forecast_at`은 1시간 단위의 정각 시각으로 관리한다.
- 요청 시각의 분이 `00~29`이면 현재 정각, `30~59`이면 다음 정각을 사용한다.
- 예: `2026-09-19 15:10 KST` → `15:00`, `15:50 KST` → `16:00`.
- 요청 `at`은 KST로 정규화한다. 서버의 현재 KST 시간대 시작보다 이른 요청은 `400 INVALID_WEATHER_REQUEST_TIME`으로 거부한다.
- 기상청 `getVilageFcst`를 한 번 호출하면 목표 예보시각 이후 항목을 시간대별로 파싱하여 `weather_grid_forecasts`에 일괄 저장한다.
- 동일 KST 날짜·격자의 캐시가 하나도 없을 때만 외부 API를 호출한다.
- 최초 후보 회차가 실패하거나 목표 예보시각을 포함하지 않으면 바로 이전 후보 회차를 한 번만 시도한다.
- 같은 날짜에 캐시가 있으면 더 최신 발표본으로 갱신하지 않는다.
- 당일 캐시가 있으나 요청한 `forecast_at` 행이 없으면 외부 API를 다시 호출하지 않고 `502 WEATHER_API_ERROR`로 처리한다.


#### 3-2. 날씨 변환 규칙

| 우선순위 | 원시값 | 최종 상태 |
| --- | --- | --- |
| 1 | `PTY=1` | `RAIN` |
| 1 | `PTY=2` | `RAIN_SNOW` |
| 1 | `PTY=3` | `SNOW` |
| 1 | `PTY=4` | `SHOWER` |
| 2 | 강수 없음 + `SKY=1` | `CLEAR` |
| 2 | 강수 없음 + `SKY=2` 또는 `3` | `CLOUDY` |
| 2 | 강수 없음 + `SKY=4` | `OVERCAST` |

---
