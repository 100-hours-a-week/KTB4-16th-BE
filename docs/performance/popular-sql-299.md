# 인기곡 집계 SQL 개선

관련 이슈: [wiki #299](https://github.com/100-hours-a-week/KTB4-16th-wiki/issues/299)

## 변경 전후

기존에는 동일한 최근 7일·미삭제·장소 조건으로 전체 COUNT와 곡별 GROUP을 각각 실행했다. 이제 전체 곡 그룹의 count를 long으로 합산한다.

```java
// 이전: 전체 건수를 별도 SQL로 조회한다.
long total = recordRepository.countActiveRecordsAtPlaces(placeIds, cutoff);
List<PopularTrackAggregateDto> groups = recordRepository.findPopularTrackAggregates(placeIds, cutoff);

// 이후: LIMIT 없는 전체 곡별 집계의 부분합을 재사용한다.
List<PopularTrackAggregateDto> groups = recordRepository.findPopularTrackAggregates(placeIds, cutoff);
long total = groups.stream().mapToLong(PopularTrackAggregateDto::count).sum();
```

곡별 집계 SQL, 순위(count DESC → latestCreatedAt DESC → trackId ASC), DTO, 트랜잭션을 유지한다. 기존 COUNT 메서드는 독립적인 검증과 기존 사용처를 위해 유지한다. LIMIT·페이징이나 기록→곡 필수 관계가 바뀌면 합산 전제를 다시 검토해야 한다.

## 복합 인덱스

새 `V12__index_popular_track_aggregation.sql`이 아래 인덱스를 생성한다.

```sql
CREATE INDEX idx_records_popular_place_active_created_track
    ON records (place_id, deleted_at, created_at, music_track_id);
```

최신 develop의 마지막 버전 V11을 확인했고 기존 V1–V11은 변경하지 않았다. MySQL은 외래키를 지원할 수 있는 새 인덱스가 생기면 기존 place_id 단일 지원 인덱스를 정리할 수 있다. GROUP의 임시표·최종 정렬과 music_tracks 조인은 남는다. 배포 전 다른 Flyway PR과 버전 충돌을 다시 확인한다.

## 실험 근거

- 로컬 단일 앱, MySQL 2CPU, Hikari 3개.
- 합성 사용자 100명·장소 1,000개·곡 100개·기록 1만/10만 건.
- 장소 100개 집합 20종류를 80RPS·60초 반복. 같은 30초·20RPS 예열.
- 후보 읽기 40회, 쓰기 유효 9회, 조합·교차 12회 후 새 본측정 24회.
- 아래는 Redis off의 세 번 측정한 run별 지표 중앙값이다. pooled percentile이나 운영 SLO가 아니다.

| 10만 기록 | 기존 SQL | COUNT 제거 + 인덱스 B |
|---|---:|---:|
| p95 ms | 2589.525 | 22.207 |
| p99 ms | 2694.659 | 140.288 |
| 실제 RPS | 42.910 | 79.998 |
| app+DB+idle Redis CPU ms/성공 | 53.132 | 14.541 |
| 세 번의 미시작 요청 합 | 6391 | 0 |

1만 건의 p95는 13.883→14.163ms로 개선되지 않았다. COUNT 제거만으로 10만 건·80RPS 병목이 해소되지 않았고 인덱스 B의 효과가 크게 관측됐다. API 대기열 완화가 포함된 결과이므로 단일 SQL이 99% 빨라졌다는 뜻은 아니다. SQL 진단 54/54에서 COUNT와 전체 그룹 합계가 일치했다.

## 쓰기·공간 비용

인덱스 B/기존의 create p95 중앙값은 50.297/50.527ms, soft delete는 21.881/23.688ms였다. 하지만 create p99는 91.488/70.280ms, DB block write 경계 구간 중앙값은 93.266/81.766MiB로 높았다. I/O는 백그라운드 flush를 포함하므로 개별 API 비용으로 귀속하지 않는다.

10만 건에서 INDEX_LENGTH 추정 차이 4MiB와 FILE_SIZE 차이 7MiB는 이전 복원 이력이 남은 할당값이다. 순수 신규 인덱스 크기가 아니다. 빈 스키마에서의 인덱스 생성 시간도 운영 online 생성 시간으로 해석하지 않는다.

## 검증·PR 범위

합산·int 범위 초과·빈 결과·순위를 검증하고, 기존 MySQL repository 테스트와 실제 information_schema 인덱스 컬럼 순서를 확인한다. 실제 로컬 검증 결과는 PR 본문에 기록한다. 부하 코드·k6·합성 시드·Grafana·JAR·개인 .env는 제품 PR에서 제외해 별도 보존했다.

Redis 의존성, 공개 API, 인증, 환경변수 변경은 없다. 이 문서의 성능 수치는 이전 고정 실험의 결과이며 이번 PR JAR로 대규모 부하를 다시 측정한 수치는 아니다.

## 근거

- [MySQL 복합 인덱스](https://dev.mysql.com/doc/refman/8.4/en/multiple-column-indexes.html)
- [EXPLAIN ANALYZE](https://dev.mysql.com/doc/refman/8.4/en/explain.html)
- [외래키 지원 인덱스](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)
