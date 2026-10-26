# 로컬 인기곡 Redis 캐시

관련 이슈: [wiki #299](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/299)

## 범위

기존 인기곡 API에서 동일한 장소 집합의 결과를 Redis에 저장하는 로컬 opt-in 기능이다. 사용자별 지역 통계·대화·알림·Pub/Sub는 포함하지 않는다. SQL COUNT 제거와 인덱스는 별도 PR이며 이 PR의 PlaceService는 develop과 동일하다. 두 PR은 독립적으로 검토할 수 있다.

활성화한 경우에만 서비스 바깥 aspect가 장소 ID를 정렬·중복 제거해 키를 만든다. hit이면 DTO를 복원하고 miss이면 원래 서비스를 실행한 뒤 결과를 저장한다. 사용자 ID는 인기곡 키에 포함하지 않는다. 일부 장소만 겹치는 다른 조합은 다른 키다.

## 로컬 실행

```bash
docker compose -f compose.redis-local.yml up -d --wait

# 신뢰할 수 있는 .env를 먼저 셸에 로드한 뒤 실행한다.
SPRING_PROFILES_ACTIVE=local,popular-cache \
MULO_AGGREGATE_CACHE_ENABLED=true \
./gradlew bootRun
```

Gradle은 .env를 자동 로드하지 않는다. DB 연결은 기존 로컬 환경을 따르므로 대상 DB를 확인한다. 자동 DB 초기화 명령은 없다. 일반 실행에서는 Redis 빈·연결을 생성하지 않는다. popular-cache 프로필과 enabled=true가 모두 있어야 캐시가 동작한다.

| 환경변수 | 기본값 |
|---|---|
| MULO_AGGREGATE_CACHE_ENABLED | false |
| MULO_AGGREGATE_CACHE_HOST | 127.0.0.1 |
| MULO_AGGREGATE_CACHE_PORT | 16382 |
| MULO_AGGREGATE_CACHE_NAMESPACE | mulo:popular-cache-local |
| MULO_AGGREGATE_CACHE_TTL | 30s |

Compose는 loopback 16382에만 노출하며 1CPU/512MiB, maxmemory 256MiB/allkeys-lru, 영구 볼륨 없음으로 구성한다. 운영용 인증·TLS·외부 접속 설정은 제공하지 않는다. 다른 앱/DB를 함께 실행하면 namespace를 구분한다. 종료는 `docker compose -f compose.redis-local.yml stop`이다.

## 만료·무효화·장애 동작

- TTL은 설정 수명과 집계 기록이 최근 7일 범위에서 빠지는 시각 중 빠른 쪽이다. miss 때 경계 MIN SQL을 추가 조회한다.
- cutoff와 만료 변환은 기존 PlaceService의 LocalDateTime.now()와 같은 JVM 기본 시간대를 사용한다. 주입 Clock의 시간대가 다르더라도 기준을 맞춘다.
- create/delete/withdraw가 commit되면 전체 인기곡 캐시 세대를 변경한다. rollback은 무효화하지 않는다. comment 수정은 인기곡 집계 내용을 바꾸지 않는다.
- 옛 세대에서 시작한 loader는 세대가 바뀌면 저장을 생략한다. 옛 키는 TTL로 만료된다.
- 같은 키의 동시 miss는 NX/PX 토큰 잠금(10초)을 사용한다. 최대 500ms 대기 후 DB로 우회하며 토큰이 일치할 때만 Lua로 잠금을 해제한다.
- 연결 300ms/명령 100ms 제한과 1초 재시도 간격을 둔다. Redis 장애는 DB 조회로 우회하며 성공한 loader를 두 번 실행하지 않는다. 복구 후 첫 조회는 세대를 회전한다.
- 기존 외부 DB 트랜잭션 안의 조회는 캐시를 우회한다.

## 전체 세대 무효화의 선택 이유와 후속 후보

이번 구현은 무효화 경로를 단순하게 유지하기 위해 공용 세대 UUID 하나를 사용한다. 기록 생성·삭제·탈퇴의 DB commit 이후 새 UUID로 교체하고, 조회·저장 시 세대를 재확인한다. 이전 세대의 payload는 별도로 전체 삭제하지 않고 TTL로 정리한다. DB 행 버전이나 프론트 ETag가 아니다.

| 구분 | 현재 전체 세대 | 후속 후보인 장소별 세대 |
|---|---|---|
| 변경 영향 | 관계없는 장소 조합도 무효화 | 변경 장소가 포함된 조합만 무효화 |
| 조회 비용 | 공용 세대 확인 | 요청 장소들의 버전 조회·재확인 |
| 쓰기 처리 | 공용 UUID 교체 | 생성·삭제·탈퇴가 영향을 준 장소 ID 확보 후 해당 버전 교체 |
| 한계 | 쓰기가 빈번하면 재사용률 하락 | 버전 관리 공간·조회 비용·동시성 처리가 추가 |

장소별 버전은 이 PR에 구현하지 않았다. 채택하려면 최대 100개 장소의 버전을 일괄 읽는 비용, 대량 탈퇴 무효화, 버전 키 유실 시 이전 값 재사용 방지, 조회·쓰기 동시성과 DB/Redis 사이 실패 처리를 검증해야 한다. 장소별 집계를 합치는 방식과도 구분한다. 장소별 버전으로 바꾸어도 저장 결과는 장소 조합 전체의 인기곡 응답일 수 있다.

이전 반복 조회 성능은 쓰기 부하에서 전역 무효화의 효과를 입증한 결과가 아니다. 후속 비교에서는 조회만 반복 / 무관한 장소에 쓰기 / 인기 장소에 쓰기 집중 조건에서 적중률, DB 집계 횟수, p95·p99, CPU, 쓰기 응답 시간을 함께 측정해야 한다.

현재 allkeys-lru는 payload뿐 아니라 세대·잠금 키도 퇴출할 수 있다. 잠금은 중복 계산 완화용이며 실행이 항상 한 번이라는 보장은 아니다. JSON 형식의 별도 스키마 버전은 도입하지 않았다. 서버 이미지는 redis:8.2.10 태그를 지정했으며 digest까지 고정한 구성은 아니다.

## 계측·검증

서비스 호출 분모는 `mulo_aggregate_cache_api_requests_total{cache="popular"}`다. hit/miss/load/wait/bypass/error 등을 분리한다. miss→wait→hit는 같은 요청에서 발생할 수 있어 사건 합계를 요청 수로 사용하지 않는다. 사용자·장소 ID를 메트릭 태그에 넣지 않는다.

```bash
# 일반 테스트는 Redis 없이 실행 가능하다.
# 실제 전용 Redis 계약 테스트는 아래처럼 명시적으로 실행한다.
MULO_POPULAR_REDIS_TEST=true ./gradlew test
```

실제 Redis 테스트는 고정 포트 16382와 테스트별 UUID namespace만 사용한다. 자신이 만든 키만 정리하고 전체 flush를 하지 않는다. 실제 API 검증 및 전체 테스트 결과는 PR 본문에 기록한다.

## 성능 근거와 한계

이전 고정 실험의 10만 건·장소 집합 20종 반복·80RPS·60초에서는 기존 SQL의 캐시 off/on p95가 2589.525→6.369ms, 전체 CPU가 53.132→3.679ms/성공이었다. 개선 SQL의 off/on은 별도 조합에서 22.207→5.084ms, 14.541→3.280ms/성공이었다. 세 번의 run별 중앙값이며 이 PR 자체에 SQL 개선이 들어간 것은 아니다.

새 키를 재사용 없이 조회하면 hit 0%였고 개선 SQL의 p95는 30.823→41.302ms로 악화됐다. cold20에서는 off GROUP 20회/on 1회가 관측됐다. 동일 장소 집합의 재사용에 따른 조건부 이득이며, 운영 일괄 활성화를 뜻하지 않는다. 이번 PR JAR의 로컬 API 검증은 새로운 성능 측정이 아니다.

전역 무효화는 쓰기가 잦으면 적중률을 낮출 수 있다. 다중 앱·replica 지연·DB commit 직후 프로세스 종료·부분 단절의 최신성은 미검증이다. DB/Redis 원자성을 보장하지 않는다. 부하 도구와 원시 결과·Grafana는 별도 보존한다.

## 근거

- [Redis cache-aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
- [SET NX/PX](https://redis.io/docs/latest/commands/set/)
- [Spring afterCommit](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCommit())
- [Java LocalDateTime.now](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/LocalDateTime.html#now())
