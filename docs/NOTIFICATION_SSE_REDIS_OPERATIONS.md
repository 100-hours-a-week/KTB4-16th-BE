# SSE Redis 운영 설정 및 배포

## 적용 범위

SSE Redis #332의 전달 모드 활성화, 장애 복구, 다중 인스턴스 검증 이후에 적용하는 운영 절차다.
알림 원본은 MySQL의 notifications에 저장하며, Redis에는 수신자 ID와 신호 버전만 전달한다.
API 경로와 응답 형식, DB 스키마, 기존 알림 생성 트랜잭션은 유지한다.

기본 전달 모드는 모든 프로필에서 local이다. prod 프로필도 SSE를 자동으로 redis로 전환하지 않는다.
Redis 활성화 시점은 클라우드 마이그레이션과 배포 승인이 끝난 뒤 별도로 결정한다.

## 환경변수

| 환경변수 | Spring 속성 | 기본값 | 범위 |
|---|---|---|---|
| MULO_NOTIFICATION_SSE_MODE | mulo.notification.sse.mode | local | local 또는 redis |
| MULO_NOTIFICATION_SSE_CHANNEL | mulo.notification.sse.channel | mulo:notification:sse:created:v1 | Redis 발행·구독 채널 |
| MULO_NOTIFICATION_SSE_HEARTBEAT_INTERVAL | mulo.notification.sse.heartbeat-interval | 25s | SSE Heartbeat 주기 |
| MULO_NOTIFICATION_SSE_MAXIMUM_CONNECTION_DURATION | mulo.notification.sse.maximum-connection-duration | 1h | SSE 최대 연결 시간 |
| MULO_NOTIFICATION_SSE_RECOVERY_CHECK_INTERVAL | mulo.notification.sse.recovery-check-interval | 1s | Redis 구독 복구 확인 주기 |
| REDIS_HOST | mulo.redis.host | 일반 프로필 127.0.0.1, prod 미지정 | 공통 Redis 호스트 |
| REDIS_PORT | mulo.redis.port | 일반 프로필 16382, prod 0 | 공통 Redis 포트 |
| REDIS_CONNECT_TIMEOUT | mulo.redis.connect-timeout | 300ms | TCP 연결 제한시간 |
| REDIS_INITIALIZATION_TIMEOUT | mulo.redis.initialization-timeout | 5s | HELLO 등 연결 초기화 제한시간 |
| REDIS_COMMAND_TIMEOUT | mulo.redis.command-timeout | 100ms | 일반 명령·구독 PING 제한시간 |

redis 모드의 채널은 공백일 수 없다. 시간 설정은 양수로 지정한다.
최대 SSE 연결 시간은 Access Token 만료 시각과 설정한 최대 연결 시간 중 더 이른 시각까지다.
복구 확인 주기는 장애 감지·복구 완료 시간의 상한이 아니다. 연결·명령 제한시간과 실행 지연도 영향을 준다.

REDIS_* 설정은 세션 폐기 조회, 기억 검색, 인기곡 캐시 등 기존 Redis 기능도 공유한다.
공통 호스트·포트·제한시간을 바꿀 때 해당 기능도 함께 검증한다.
local은 알림 전달 모드만 선택하므로, 다른 기능의 Redis 연결까지 비활성화하지 않는다.

.env.example은 로컬 예제다. Spring Boot와 Gradle은 .env를 자동으로 읽지 않는다.
실제 값은 승인된 배포 환경변수·비밀정보 관리 경로로 주입한다.
비밀번호, Access Token, 실제 운영 접속 정보는 문서나 Git에 기록하지 않는다.

## 환경별 채널

같은 서비스 환경의 모든 인스턴스는 같은 Redis와 같은 채널을 사용해야 한다.
서로 다른 환경은 Redis를 분리하거나 명시적으로 다른 채널을 선택한다.

| 환경 예시 | MULO_NOTIFICATION_SSE_CHANNEL |
|---|---|
| 개발 | mulo:notification:sse:develop:created:v1 |
| 운영 | mulo:notification:sse:prod:created:v1 |

미지정 시 기존 채널을 그대로 사용하므로 PR1~PR3과 기본 채널이 호환된다.
채널 변경도 전달 모드 변경과 같은 전환 절차를 따른다.
같은 환경에서 구 채널과 새 채널을 사용하는 인스턴스에 동시에 트래픽을 보내면 인스턴스 간 신호가 누락된다.

Redis Pub/Sub은 재전송을 보장하지 않으며 Redis DB 번호로 채널이 격리되지 않는다.
전달 특성과 환경별 채널 구분 근거는 [Redis Pub/Sub 공식 문서](https://redis.io/docs/latest/develop/pubsub/)를 따른다.

## 배포 전 확인

1. PR1 → PR2 → PR3 → PR4를 각각 순서대로 머지하고, 각 단계의 선행 변경을 포함한 이미지인지 확인한다.
2. 대상 환경의 Redis 주소, 네트워크 경로, 채널, 모든 인스턴스의 동일한 설정을 클라우드팀과 확인한다.
3. 현재 RedisClient 구성은 호스트·포트 기반 연결만 제공한다. TLS·사용자명·비밀번호 설정 경로는 구현되어 있지 않다. 대상 Redis에서 이를 요구하면 활성화를 보류하고 별도 구성 변경을 승인받는다.
4. 신규 연결의 connected 이벤트와 기존 REST 재조회가 클라이언트에서 처리되는지 확인한다. 주기적 재조회 여부도 별도로 확인한다.
5. 중간에 다른 모드·채널이 섞이지 않도록 전체 인스턴스 전환과 트래픽 재개 방법을 정한다.
6. 이전 이미지 태그와 설정 묶음, 클라우드팀이 관리하는 롤백 경로를 보관한다.

Redis에 PING, SUBSCRIBE, UNSUBSCRIBE, PUBLISH가 허용되는지 확인한다.
클라이언트 초기화와 세션 등 다른 Redis 기능에 필요한 명령 권한은 별도로 확인한다.
구독 ACK와 PING 성공, PUBLISH 반환값만으로 브라우저 수신까지 보장되지는 않는다.

## 현재 저장소의 설정 주입 위치

| 경로 | 현재 코드의 역할 | 운영 반영 시 확인 |
|---|---|---|
| .github/workflows/cd-dev.yml | backend.env 생성 후 GCP 개발 VM으로 전송하고 Compose의 backend 교체 | 생성기에 필요한 Redis·SSE 변수 반영, VM Compose의 env_file/environment 전달 확인 |
| .github/workflows/cd-prod.yml | Terraform 바탕 템플릿을 복제하고 이미지 태그를 바꿔 GCP MIG 배포 | 바탕 템플릿이 참조하는 env 비밀정보와 시작 스크립트에서 변수 전달 확인 |
| .env.example | 개발자가 복사할 로컬 설정 예제 | 운영 서버에 자동 반영되는 파일이 아님 |

현재 cd-dev.yml의 backend.env 생성 목록에는 Redis·SSE 변수가 없다.
VM의 backend.env를 수동 편집해도 다음 배포 때 다시 생성되므로 지속적인 설정 반영 방법으로 사용하지 않는다.
운영 CD도 애플리케이션 YAML이나 .env.example을 운영 비밀정보로 자동 변환하지 않는다.
실제 클라우드 마이그레이션 이후의 배포 경로와 변수 주입은 클라우드팀이 확정해야 한다.
이 문서의 GCP 경로는 현재 저장소 코드 기준이며, 클라우드 환경에 반영되었다는 의미는 아니다.

## 최초 활성화 및 채널 전환

1. 기존 모드·채널을 유지한 채 PR4까지 포함한 코드를 모든 인스턴스에 배포한다.
2. 점검 창에서 SSE 트래픽 진입을 멈추거나 전체 인스턴스를 함께 전환할 수 있는 승인된 절차를 적용한다.
3. 모든 인스턴스에 MULO_NOTIFICATION_SSE_MODE=redis와 동일한 환경별 채널, 승인된 REDIS_HOST·REDIS_PORT를 주입해 재시작한다.
4. 각 인스턴스에서 'SSE 알림 Redis 구독이 준비되었습니다.' 로그를 확인한다.
5. 아래 실제 수신 검증과 REST 재조회를 통과한 뒤 트래픽을 재개한다.

일반 롤링 배포의 HTTP health 통과만으로 모드·채널 전환 완료를 판단하지 않는다.
이미 redis 모드·같은 채널을 사용하는 코드끼리의 롤링 배포와 최초 모드 전환을 구분한다.

## 실제 수신 검증

두 인스턴스에 각각 도달하는 승인된 경로와 검증용 사용자 Access Token을 준비한다.
로드밸런서 뒤의 같은 URL에 두 번 연결한 것만으로 서로 다른 인스턴스에 연결되었다고 판단하지 않는다.

각 터미널에서 다음과 같이 실제 SSE 스트림을 연다.

```bash
curl --no-buffer \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  "$INSTANCE_A_URL/api/notifications/stream"
```

두 번째 터미널에서는 INSTANCE_B_URL을 사용한다. 토큰은 URL 쿼리에 넣지 않는다.

1. 같은 수신자의 A 인스턴스 두 탭과 B 인스턴스 한 탭에서 connected 이벤트를 확인한다.
2. A에서 기존 친구 요청 기능으로 알림을 생성하고 세 탭의 notification-created 이벤트와 REST 목록의 알림 1건을 확인한다.
3. 새 수신자를 사용해 B에서 생성한 알림이 A로 전달되는지도 확인한다.
4. 같은 Outbox 이벤트의 재처리는 신규 알림·SSE 신호를 추가로 만들지 않는지 격리 테스트에서 확인한다.
5. 다른 사용자의 스트림에는 해당 알림이 전달되지 않는지 확인한다.
6. 검증용 환경에서 한 인스턴스의 구독 연결만 끊고 그 인스턴스의 기존 스트림 종료, 다른 인스턴스의 연결 유지, 재구독 후 새 스트림 수신을 확인한다.
7. 연결이 끊긴 동안 생성한 알림을 재연결 뒤 GET /api/notifications와 GET /api/notifications/unread-status로 다시 확인한다.

신호 본문은 data:{}이며, 알림 원문·누락 복구는 REST 조회로 수행한다.
알림 생성 트랜잭션과 사용자별 전달 결과는 실제 기능과 DB 저장 결과를 함께 확인한다.

## 장애 동작과 관찰

| 상황 | 현재 처리 |
|---|---|
| 시작 시 Redis 구독 실패 | 애플리케이션은 시작을 계속하고 구독 복구를 재시도하며, SSE 전달 불가 상태에서는 신규 스트림을 즉시 종료 |
| 구독 단절·구독 PING 실패 | 해당 인스턴스의 기존 SSE 연결 종료, 재구독과 상태 확인 후 신규 연결 허용 |
| 신호 발행·작업 제출 실패 | 발행한 인스턴스에서 해당 수신자의 로컬 SSE 연결 종료 |
| Redis 복구 | 새 구독과 새 SSE 연결에서 이후 신호 수신. 끊긴 동안의 과거 신호는 재생하지 않음 |

발행 실패만으로 다른 인스턴스의 SSE 연결까지 닫히지는 않는다.
클라이언트는 SSE 신호만으로 정합성을 판단하지 않고 재연결·주기적 REST 조회를 통해 DB 저장 상태를 다시 확인해야 한다.
이 문서는 프론트의 재조회 구현 완료를 보장하지 않는다.

Pub/Sub 연결만 끊긴 경우와 Redis 전체 장애를 구분한다.
Redis 전체 장애에서는 세션 폐기 검사 등 기존 인증 경로도 영향을 받을 수 있어 REST 조회가 항상 성공한다고 보장할 수 없다.
기존 HTTP health는 SSE 전달 준비 상태를 대신하지 않는다.

관찰할 기존 로그는 구독 준비, 구독 중단, 구독 복구 실패, 신호 발행 실패다.
failureType과 발생 시각, 영향받은 인스턴스를 함께 확인하고 실제 알림 저장·REST 조회·SSE 수신 결과로 복구를 판정한다.
토큰·알림 원문을 추가 로깅하지 않는다. CLIENT PAUSE는 Redis 전체에 영향을 주므로 격리된 로컬 테스트에서만 사용한다.

## 롤백

1. 트래픽을 제어한 상태에서 이전에 검증한 이미지와 설정 묶음을 모든 인스턴스에 복원한다.
2. 환경별 채널을 지원하지 않는 이전 코드로 돌아갈 때는 모든 인스턴스가 기존 채널을 사용하도록 설정도 함께 되돌린다.
3. local 모드로 복원하면 다중 인스턴스 간 실시간 전달은 제공되지 않는다. 트래픽 구성과 REST 재조회를 포함해 서비스 동작을 확인한다.
4. 각 인스턴스의 모드·채널 일치, 기존 연결 재연결, 알림 목록·미읽음 상태를 확인한 뒤 트래픽을 재개한다.

MySQL 알림·Outbox 데이터나 기존 Redis 데이터를 지워 롤백하지 않는다.
다른 기능도 사용하는 Redis 설정을 SSE만 고려해 비우지 않는다.

## 격리된 로컬 회귀 검증

JDK 21과 Docker를 사용할 수 있는 터미널에서 저장소 루트를 기준으로 실행한다.
Gradle의 일반 로컬 test는 127.0.0.1:3313의 mulo_test DB와 테스트 전용 계정을 사용하고 실행 전 컨테이너를 확인한다.
운영 .env를 로드해 테스트 DB를 선택하지 않는다. 기존 MySQL 테스트 컨테이너·볼륨은 정리하지 않는다.

기본 실행에서는 실제 Redis 장애 테스트 2개와 다중 인스턴스 테스트 3개를 건너뛴다.

```bash
docker compose -f compose.test.yml up -d
./gradlew test --tests 'com.ktb4.team16.mulo.notification.sse.*'
```

실제 Redis 테스트는 서로 다른 전용 포트 16384·16385를 사용한다.
해당 이름이나 포트가 이미 사용 중이면 기존 서비스를 종료하지 말고 먼저 사용자를 확인한다.
기존 인기곡 캐시용 compose.redis-local.yml의 Redis를 장애 테스트에 재사용하지 않는다.

```bash
docker run --rm --detach --name mulo-sse-recovery-332-verify \
  --publish 127.0.0.1:16384:6379 \
  redis:7-alpine redis-server --save "" --appendonly no

docker run --rm --detach --name mulo-sse-multi-instance-332-verify \
  --publish 127.0.0.1:16385:6379 \
  redis:7-alpine redis-server --save "" --appendonly no

MULO_NOTIFICATION_SSE_REDIS_TEST=true \
MULO_NOTIFICATION_SSE_MULTI_INSTANCE_TEST=true \
./gradlew test --tests 'com.ktb4.team16.mulo.notification.sse.*'

MULO_NOTIFICATION_SSE_REDIS_TEST=true \
MULO_NOTIFICATION_SSE_MULTI_INSTANCE_TEST=true \
./gradlew clean build checkstyleMain checkstyleTest checkstyleIntegrationTest
```

결과는 build/reports/tests/test/index.html과 build/reports/checkstyle에서 확인한다.
다중 인스턴스 테스트는 같은 JVM의 독립 Spring/Tomcat 두 개, 실제 HTTP·Redis·MySQL을 사용한다.
별도 JVM, 실제 로드밸런서·프록시·네트워크 장애 조치, 운영 Redis 인증·TLS는 이 테스트의 검증 범위에 포함되지 않는다.
clean build와 별도 실행 태스크인 integrationTest·recordCreateConcurrencyTest도 구분한다.

검증 후에는 위 명령으로 직접 만든 Redis 두 개만 종료한다. --rm으로 각 임시 컨테이너와 익명 볼륨이 제거된다.

```bash
docker stop mulo-sse-recovery-332-verify mulo-sse-multi-instance-332-verify
```
