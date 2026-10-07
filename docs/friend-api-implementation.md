# 친구 도메인 및 친구 자물쇠 읽기 구현

> 기준: 작업번호 193, `develop`에 병합된 백엔드 PR #79, OpenAPI v2.8.
> 정책 원문은 `specs/MULO_서비스_정책_정의서_v2.1.md`의 FRIEND-001~013 및 RECORD-008~010을 따른다.

## 친구 API 7개

| 기능 | HTTP 경로 | 구현 위치 |
|---|---|---|
| 친구 목록 | `GET /api/users/me/friends` | `friend/controller/FriendController.java:22`, `friend/service/FriendQueryService.java:42` |
| 받은 요청 목록 | `GET /api/users/me/friend-requests/received` | `friend/controller/FriendRequestController.java:41`, `friend/service/FriendQueryService.java:70` |
| 보낸 요청 목록 | `GET /api/users/me/friend-requests/sent` | `friend/controller/FriendRequestController.java:52`, `friend/service/FriendQueryService.java:96` |
| 닉네임 친구 요청 | `POST /api/users/me/friend-requests` | `friend/controller/FriendRequestController.java:63`, `friend/service/FriendCommandService.java:30` |
| 요청 수락 | `POST /api/friend-requests/{friendRequestId}/accept` | `friend/controller/FriendRequestController.java:85`, `friend/service/FriendCommandService.java:69` |
| 요청 거절·취소 | `DELETE /api/friend-requests/{friendRequestId}` | `friend/controller/FriendRequestController.java:96`, `friend/service/FriendCommandService.java:96` |
| 친구 삭제 | `DELETE /api/friendships/{friendshipId}` | `friend/controller/FriendshipController.java:22`, `friend/service/FriendCommandService.java:114` |

목록 세 개는 cursor 방식이며 기본 크기는 20, 허용 범위는 1~100이다. 친구 목록은 닉네임·사용자 ID 오름차순이고 요청 목록은 생성 시각·요청 ID 내림차순이다. 빈 목록은 200과 빈 배열을 반환한다. `FriendQueryService`가 페이지 크기, 정렬 경계, 다음 cursor를 관리한다.

## 저장·상태 전이

- `FriendRequest`는 pending 요청만 나타낸다. 수락·거절·취소 후 요청 행을 삭제한다.
- `Friendship`은 방향 없는 관계다. `UserPair`가 사용자 ID를 low/high 순서로 정규화한다.
- 신규 요청은 201과 요청 ID를 반환한다. 역방향 pending이 이미 있으면 이를 삭제하고 친구 관계를 생성해 200과 관계 ID를 반환한다.
- 처음부터 동시 실행된 역방향 요청도 한 pending 생성 뒤 자동 수락으로 수렴해 친구 관계 하나를 남긴다.
- 요청 수락은 수신자만, 거절·취소는 요청 당사자만, 친구 삭제는 관계 당사자만 실행할 수 있다. 권한 없는 요청 ID·관계 ID는 404로 숨긴다.
- `FriendCommandService`는 두 활성 사용자 행을 ID 오름차순으로 잠근 뒤 같은 사용자 쌍의 상태 변경을 직렬화한다. DB의 사용자 쌍 UNIQUE 제약이 최종 중복 방어선이다.

DB 구조는 기존 `src/main/resources/db/migration/V1__init_schema.sql:144-175`의 `friend_requests`·`friendships`를 사용한다. 작업번호 193에서 Flyway migration은 추가하지 않았다.

## 친구 자물쇠 읽기

친구 목록에서 상대 사용자 ID로 이동하면 다음 읽기 API를 사용한다.

| 화면 데이터 | HTTP 경로 | 구현 위치 |
|---|---|---|
| 닉네임·총 자물쇠 수·지역 요약 | `GET /api/users/{friendUserId}/records/regions` | `record/controller/FriendRecordController.java:28` |
| 선택 지역 자물쇠 cursor 페이지 | `GET /api/users/{friendUserId}/records?legalDongCode=...` | `record/controller/FriendRecordController.java:40` |
| 자물쇠 상세 | `GET /api/records/{recordId}` | `record/service/RecordService.java:75` |

`FriendRecordReadService.java:26-66`은 친구 대시보드와 지역 페이지 **각 요청 시점**에 활성 상대 사용자와 현재 friendship을 확인한다. 상대 사용자가 없거나 친구가 아니면 404 `USER_NOT_FOUND`를 반환한다. 상세 조회는 `RecordRepository.java:122-144`에서 활성 기록·소유자·현재 friendship을 한 조회 조건으로 검사한다. 미존재·삭제·권한 없음은 동일한 404 `RECORD_NOT_FOUND`다. 따라서 친구 삭제 이후 새 요청에서는 기존에 알고 있던 ID로도 접근할 수 없다.

## 검증과 계약

- `src/test/java/com/ktb4/team16/mulo/friend/`의 서비스·컨트롤러·MySQL 통합 테스트가 요청 방향, 권한, cursor, 중복 요청, 동시 요청을 검증한다.
- `src/test/java/com/ktb4/team16/mulo/record/`의 친구 읽기·상세 테스트가 현재 친구 관계의 읽기 권한과 삭제 후 차단을 검증한다.
- 실제 MySQL 연결에 필요한 워크트리 `.env` 값을 **프로세스 환경에만** 전달해 `./gradlew test --rerun-tasks`를 실행했고 497개 테스트가 통과했다. 같은 설정의 `./gradlew clean build`도 Checkstyle·테스트·패키징까지 통과했다.
- 요청·응답 계약은 `specs/openapi/MULO_OpenAPI_v2.8.yaml`과 같은 버전의 JSON·Swagger HTML에 기록되어 있다.
