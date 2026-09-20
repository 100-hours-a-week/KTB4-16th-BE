# 날씨 API 및 일 단위 격자 캐시 구현 계획·진행 상태

**Goal:** 사용자의 위·경도와 요청시각을 가장 가까운 정각 예보로 변환하고, 기상청 단기예보를 격자당 KST 하루 한 번만 호출하여 MySQL에 시간대별로 저장한 뒤 `GET /api/weather`로 반환한다.

**Architecture:** `WeatherController -> WeatherService -> Weather cache repositories / KmaWeatherClient` 흐름을 사용한다. `WeatherService`는 트랜잭션 밖에서 외부 API 호출을 조정하고, `WeatherCacheWriter`만 짧은 쓰기 트랜잭션을 사용한다. 시간 선택, 격자 변환, 기상청 응답 변환은 각각 독립된 순수 컴포넌트로 분리한다.

**Tech Stack:** Java 21, Spring Boot 4.1.x, Spring Web MVC `RestClient`, Spring Data JPA, MySQL 8.4, Flyway, Bean Validation, JUnit 5, Mockito, MockRestServiceServer

**Spec:** `docs/weather-api-cache-design.md`

## 현재 진행 상태

- 구현 완료: V3 격자·예보 캐시 스키마, 기상청 단기예보 Client, 시간·격자 변환,
  일 단위 캐시 Service, `GET /api/weather`, 502 오류 변환
- 구현 완료: 요청 `at`을 KST로 정규화한 뒤 완료된 이전 시간대를 400
  `INVALID_WEATHER_REQUEST_TIME`으로 차단
- 구현 완료: 단위·Controller 테스트, 실제 기상청 연동 및 MySQL 일괄 저장 확인
- 보류: V1 범위 밖의 추가 경계·통합 테스트 강화

아래 체크박스는 구현 전에 작성한 작업 분해 기록이다. 현재 완료 여부의 기준은 이 문서의
`현재 진행 상태`와 `docs/weather-api-cache-design.md`이며, 체크박스를 새 작업 목록으로
해석하지 않는다.

## Global Constraints

- Redis와 신규 라이브러리를 추가하지 않는다.
- 기상청 데이터 원천은 `getVilageFcst`만 사용한다.
- 서비스 시간 기준은 `Asia/Seoul`이다.
- 요청 시각의 분이 `00~29`이면 현재 정각, `30~59`이면 다음 정각을 선택한다.
- 같은 `cache_date`와 격자는 첫 성공 응답만 저장하고 당일 다시 외부 API를 호출하지 않는다.
- 최초 외부 호출의 여러 시간대 결과를 MySQL에 일괄 저장한다.
- `PTY`가 강수 상태이면 `SKY`보다 우선한다.
- API 응답에는 `forecastAt`, `temperature`, `weatherCondition`만 포함한다.
- 기상청 API 키와 원시 응답을 로그에 남기지 않는다.
- 적용된 `V1`, `V2` migration은 수정하지 않고 `V3`를 추가한다.
- 외부 네트워크 호출을 DB 트랜잭션 안에서 수행하지 않는다.
- 날씨 API는 기존 보안 정책에 따라 Access Token 인증이 필요한 `GET` 요청으로 유지한다.
- 코드 구현, 커밋, push, PR은 계획 승인 후 별도로 진행한다.

## Review Focus

- `11:29:59`와 `11:30:00` 경계가 각각 `11:00`, `12:00`으로 정확히 선택되어야 한다.
- `baseTime=1100`에 목표 `11:00`이 없을 때 `0800` 회차를 처음부터 선택해야 한다.
- 당일 캐시 묶음은 있지만 목표 `forecast_at`이 없을 때 외부 API를 두 번째로 호출하지 않아야 한다.
- HTTP 200이어도 기상청 `resultCode`가 `00`이 아니면 성공으로 처리하지 않아야 한다.
- 동일 격자 최초 요청이 동시에 저장을 시도해 UNIQUE 경합이 발생하면 기존 캐시를 재조회해야 한다.

---

## 파일 구조

```text
weather/
├── client/
│   ├── KmaWeatherClient.java
│   └── dto/KmaVilageForecastResponse.java
├── config/KmaWeatherProperties.java
├── controller/WeatherController.java
├── domain/
│   ├── ForecastSlot.java
│   ├── GridCoordinate.java
│   └── WeatherCondition.java
├── dto/WeatherResponse.java
├── entity/
│   ├── WeatherGrid.java
│   └── WeatherGridForecast.java
├── exception/WeatherApiException.java
├── message/WeatherMessage.java
├── repository/
│   ├── WeatherGridForecastRepository.java
│   └── WeatherGridRepository.java
└── service/
    ├── GridCoordinateConverter.java
    ├── KmaForecastMapper.java
    ├── WeatherCacheWriter.java
    ├── WeatherService.java
    └── WeatherTimePolicy.java
```

---

### Task 1: MySQL 격자 및 일 단위 예보 캐시 스키마

**Files:**
- Create: `src/main/resources/db/migration/V3__create_weather_grid_forecast_cache.sql`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/domain/WeatherCondition.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/entity/WeatherGrid.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/entity/WeatherGridForecast.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/repository/WeatherGridRepository.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/repository/WeatherGridForecastRepository.java`

**Interfaces:**
- Produces: `WeatherGridRepository.findByGridXAndGridY(short, short)`
- Produces: `WeatherGridForecastRepository.findByGridAndCacheDateAndForecastAt(...)`
- Produces: `WeatherGridForecastRepository.existsByGridAndCacheDate(...)`

- [ ] **Step 1: 새 Flyway migration 작성**

```sql
CREATE TABLE weather_grids (
    weather_grid_id BIGINT NOT NULL AUTO_INCREMENT,
    grid_x SMALLINT NOT NULL,
    grid_y SMALLINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_weather_grids PRIMARY KEY (weather_grid_id),
    CONSTRAINT uk_weather_grids_xy UNIQUE (grid_x, grid_y)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE weather_grid_forecasts (
    weather_grid_forecast_id BIGINT NOT NULL AUTO_INCREMENT,
    weather_grid_id BIGINT NOT NULL,
    cache_date DATE NOT NULL,
    forecast_at DATETIME NOT NULL,
    temperature DECIMAL(3, 1) NOT NULL,
    weather_condition ENUM(
        'CLEAR', 'CLOUDY', 'OVERCAST', 'RAIN',
        'SNOW', 'RAIN_SNOW', 'SHOWER'
    ) NOT NULL,
    base_at DATETIME NOT NULL,
    fetched_at DATETIME NOT NULL,
    CONSTRAINT pk_weather_grid_forecasts PRIMARY KEY (weather_grid_forecast_id),
    CONSTRAINT fk_weather_grid_forecasts_grid
        FOREIGN KEY (weather_grid_id) REFERENCES weather_grids(weather_grid_id),
    CONSTRAINT uk_weather_grid_forecasts_daily_slot
        UNIQUE (weather_grid_id, cache_date, forecast_at),
    INDEX idx_weather_grid_forecasts_lookup
        (weather_grid_id, cache_date, forecast_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

기존 `V2__create_weather.sql`의 `weather` 테이블은 이미 적용됐을 수 있으므로 삭제하거나 수정하지 않는다. 새 구현에서는 사용하지 않는다.

- [ ] **Step 2: 날씨 상태와 JPA Entity 작성**

```java
public enum WeatherCondition {
    CLEAR, CLOUDY, OVERCAST, RAIN, SNOW, RAIN_SNOW, SHOWER
}
```

`WeatherGrid`에는 `weatherGridId`, `gridX`, `gridY`, `createdAt`을 매핑하고, `WeatherGridForecast`에는 위 migration의 전체 컬럼을 매핑한다. `forecastAt`, `baseAt`, `fetchedAt`은 DB에 KST 지역시각으로 저장하는 `LocalDateTime`을 사용한다.

- [ ] **Step 3: Repository 인터페이스 작성**

```java
Optional<WeatherGrid> findByGridXAndGridY(short gridX, short gridY);
```

```java
Optional<WeatherGridForecast> findByGridAndCacheDateAndForecastAt(
        WeatherGrid grid, LocalDate cacheDate, LocalDateTime forecastAt);

boolean existsByGridAndCacheDate(WeatherGrid grid, LocalDate cacheDate);
```

- [ ] **Step 4: migration과 JPA 매핑 검증**

Run: `docker compose up -d --wait`

Run: `./gradlew test`

Expected: Flyway가 `V3`를 적용하고 Hibernate `ddl-auto=validate`가 두 신규 테이블과 Entity 매핑을 정상 검증한다.

- [ ] **Step 5: 커밋 체크포인트**

이 단계에서는 자동 커밋하지 않는다. 실제 이슈 번호와 사용자의 커밋 승인을 받은 뒤 DB 변경 커밋을 별도로 생성한다.

---

### Task 2: 요청시각 및 기상청 발표 회차 선택

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/weather/domain/GridCoordinate.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/service/WeatherTimePolicy.java`
- Test: `src/test/java/com/ktb4/team16/mulo/weather/service/WeatherTimePolicyTest.java`

**Interfaces:**
- Produces: `WeatherTimePolicy.nearestForecastAt(OffsetDateTime): ZonedDateTime`
- Produces: `WeatherTimePolicy.candidateBaseTimes(ZonedDateTime, ZonedDateTime): List<ZonedDateTime>`

- [ ] **Step 1: 시간 경계 실패 테스트 작성**

```java
@ParameterizedTest
@CsvSource({
    "2026-09-20T11:29:59+09:00, 2026-09-20T11:00:00+09:00",
    "2026-09-20T11:30:00+09:00, 2026-09-20T12:00:00+09:00",
    "2026-09-20T23:40:00+09:00, 2026-09-21T00:00:00+09:00"
})
void choosesNearestHour(String requestedAt, String expected) {
    assertThat(policy.nearestForecastAt(OffsetDateTime.parse(requestedAt)))
            .isEqualTo(ZonedDateTime.parse(expected + "[Asia/Seoul]"));
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests '*WeatherTimePolicyTest'`

Expected: `WeatherTimePolicy`가 없어 컴파일이 실패한다.

- [ ] **Step 3: 가장 가까운 정각 계산 구현**

```java
public ZonedDateTime nearestForecastAt(OffsetDateTime requestedAt) {
    ZonedDateTime kst = requestedAt.atZoneSameInstant(KST);
    ZonedDateTime hour = kst.truncatedTo(ChronoUnit.HOURS);
    // 중요: 30분 미만은 현재 정각, 30분 이상은 다음 정각 예보를 사용한다.
    return kst.getMinute() < 30 ? hour : hour.plusHours(1);
}
```

- [ ] **Step 4: 목표 시각을 포함하는 회차 테스트 작성**

```java
@Test
void elevenTenStartsWithEightHundredBase() {
    ZonedDateTime now = ZonedDateTime.parse("2026-09-20T11:10:00+09:00[Asia/Seoul]");
    ZonedDateTime target = ZonedDateTime.parse("2026-09-20T11:00:00+09:00[Asia/Seoul]");

    assertThat(policy.candidateBaseTimes(now, target))
            .startsWith(ZonedDateTime.parse("2026-09-20T08:00:00+09:00[Asia/Seoul]"));
}
```

- [ ] **Step 5: 회차 후보 구현**

```java
private static final List<Integer> BASE_HOURS =
        List.of(2, 5, 8, 11, 14, 17, 20, 23);

public List<ZonedDateTime> candidateBaseTimes(ZonedDateTime now, ZonedDateTime target) {
    List<ZonedDateTime> bases = Stream.of(now.toLocalDate(), now.toLocalDate().minusDays(1))
            .flatMap(date -> BASE_HOURS.stream()
                    .map(hour -> date.atTime(hour, 0).atZone(KST)))
            // 중요: 단기예보 첫 슬롯은 발표시각 다음 정각부터이므로 목표시각 이전 회차만 허용한다.
            .filter(base -> !base.isAfter(now) && !base.plusHours(1).isAfter(target))
            .sorted(Comparator.reverseOrder())
            .toList();
    return bases.stream().limit(2).toList();
}
```

- [ ] **Step 6: 시간 정책 테스트 통과 확인**

Run: `./gradlew test --tests '*WeatherTimePolicyTest'`

Expected: 경계값, 자정 넘김, `11:10 -> 0800`, `11:40 -> 1100` 테스트가 모두 PASS한다.

- [ ] **Step 7: 커밋 체크포인트**

자동 커밋하지 않고 실제 이슈 번호와 사용자 승인을 기다린다.

---

### Task 3: 위·경도 격자 변환과 날씨 상태 변환

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/weather/service/GridCoordinateConverter.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/domain/ForecastSlot.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/service/KmaForecastMapper.java`
- Test: `src/test/java/com/ktb4/team16/mulo/weather/service/GridCoordinateConverterTest.java`
- Test: `src/test/java/com/ktb4/team16/mulo/weather/service/KmaForecastMapperTest.java`

**Interfaces:**
- Consumes: latitude, longitude, KMA `TMP`, `SKY`, `PTY`
- Produces: `GridCoordinateConverter.convert(double, double): GridCoordinate`
- Produces: `KmaForecastMapper.toCondition(String, String): WeatherCondition`
- Produces: `ForecastSlot(LocalDateTime, BigDecimal, WeatherCondition)`

- [ ] **Step 1: 서울 격자 변환 실패 테스트 작성**

```java
@Test
void convertsSeoulCoordinateToKmaGrid() {
    GridCoordinate grid = converter.convert(37.5665, 126.9780);
    assertThat(grid).isEqualTo(new GridCoordinate((short) 60, (short) 127));
}
```

- [ ] **Step 2: 공식 DFS 격자 변환식 구현**

기상청 격자 위·경도 자료의 Lambert Conformal Conic 상수 `RE=6371.00877`, `GRID=5.0`, `SLAT1=30.0`, `SLAT2=60.0`, `OLON=126.0`, `OLAT=38.0`, `XO=43`, `YO=136`을 사용한다. 결과는 기상청 지침대로 `floor(value + 0.5)` 반올림하여 `short`로 반환한다.

```java
public record GridCoordinate(short x, short y) {
}
```

시간대별 파싱 결과는 다음 불변 DTO로 전달한다.

```java
public record ForecastSlot(
        LocalDateTime forecastAt,
        BigDecimal temperature,
        WeatherCondition weatherCondition) {
}
```

- [ ] **Step 3: 날씨 상태 우선순위 테스트 작성**

```java
@ParameterizedTest
@CsvSource({
    "0,1,CLEAR", "0,3,CLOUDY", "0,4,OVERCAST",
    "1,1,RAIN", "2,1,RAIN_SNOW", "3,1,SNOW", "4,1,SHOWER"
})
void mapsPtyBeforeSky(String pty, String sky, WeatherCondition expected) {
    assertThat(mapper.toCondition(pty, sky)).isEqualTo(expected);
}
```

- [ ] **Step 4: 날씨 상태 변환 구현**

```java
public WeatherCondition toCondition(String pty, String sky) {
    return switch (pty) {
        case "1" -> WeatherCondition.RAIN;
        case "2" -> WeatherCondition.RAIN_SNOW;
        case "3" -> WeatherCondition.SNOW;
        case "4" -> WeatherCondition.SHOWER;
        case "0" -> switch (sky) {
            case "1" -> WeatherCondition.CLEAR;
            case "3" -> WeatherCondition.CLOUDY;
            case "4" -> WeatherCondition.OVERCAST;
            default -> throw new IllegalArgumentException("Unsupported SKY value");
        };
        default -> throw new IllegalArgumentException("Unsupported PTY value");
    };
}
```

- [ ] **Step 5: 순수 변환 테스트 실행**

Run: `./gradlew test --tests '*GridCoordinateConverterTest' --tests '*KmaForecastMapperTest'`

Expected: 서울 격자와 일곱 날씨 상태 변환이 모두 PASS한다.

- [ ] **Step 6: 커밋 체크포인트**

자동 커밋하지 않고 실제 이슈 번호와 사용자 승인을 기다린다.

---

### Task 4: 기상청 단기예보 클라이언트와 응답 파싱

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/weather/config/KmaWeatherProperties.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/client/dto/KmaVilageForecastResponse.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/client/KmaWeatherClient.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/exception/WeatherApiException.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/ktb4/team16/mulo/weather/client/KmaWeatherClientTest.java`

**Interfaces:**
- Produces: `KmaWeatherClient.fetch(GridCoordinate, ZonedDateTime): List<ForecastSlot>`
- Throws: `WeatherApiException` for transport, non-`00`, malformed JSON, missing `TMP/SKY/PTY`

- [ ] **Step 1: 설정 프로퍼티 작성**

```yaml
kma:
  base-url: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0
  service-key: ${KMA_SERVICE_KEY}
```

```java
@ConfigurationProperties("kma")
public record KmaWeatherProperties(String baseUrl, String serviceKey) {
}
```

`MuloApplication` 또는 별도 설정에 `KmaWeatherProperties` 등록을 추가한다. 서비스 키는 환경변수에서만 읽는다.

- [ ] **Step 2: 성공 응답 파싱 실패 테스트 작성**

Mock 응답에 같은 `fcstDate + fcstTime`의 `TMP`, `SKY`, `PTY` 항목과 다음 시간대 항목을 넣고 두 개의 `ForecastSlot`으로 그룹화되는지 검증한다.

```java
assertThat(client.fetch(new GridCoordinate((short) 60, (short) 127), baseAt))
        .extracting(ForecastSlot::forecastAt)
        .containsExactly(
                LocalDateTime.of(2026, 9, 20, 11, 0),
                LocalDateTime.of(2026, 9, 20, 12, 0));
```

- [ ] **Step 3: 응답 DTO와 클라이언트 구현**

`response.header.resultCode`, `resultMsg`, `response.body.items.item`만 매핑한다. 각 item은 `baseDate`, `baseTime`, `category`, `fcstDate`, `fcstTime`, `fcstValue`, `nx`, `ny`를 가진다.

```java
public List<ForecastSlot> fetch(GridCoordinate grid, ZonedDateTime baseAt) {
    URI uri = buildEncodedUri(grid, baseAt);
    KmaVilageForecastResponse response = restClient.get().uri(uri)
            .retrieve().body(KmaVilageForecastResponse.class);
    validateNormalService(response);
    return mapper.groupByForecastTime(response.response().body().items().item(), baseAt);
}
```

발급키가 이미 URL 인코딩된 형태일 수 있으므로 `serviceKey`를 다시 인코딩하지 않는다. 완성된 URI나 query string을 로그에 출력하지 않는다.

- [ ] **Step 4: 오류 응답 테스트 작성**

`03 NO_DATA`, `01 APPLICATION_ERROR`, HTTP 5xx, 빈 body, `TMP/SKY/PTY` 누락을 각각 `WeatherApiException`으로 변환하는 테스트를 작성한다. 예외 메시지에는 서비스 키와 원문 응답을 넣지 않는다.

- [ ] **Step 5: 클라이언트 테스트 실행**

Run: `./gradlew test --tests '*KmaWeatherClientTest'`

Expected: 정상 그룹화와 모든 실패 변환 테스트가 PASS한다.

- [ ] **Step 6: 커밋 체크포인트**

자동 커밋하지 않고 실제 이슈 번호와 사용자 승인을 기다린다.

---

### Task 5: 일 단위 캐시 조회 및 최초 적재 Service

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/weather/service/WeatherCacheWriter.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/service/WeatherService.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/dto/WeatherResponse.java`
- Create: `src/main/java/com/ktb4/team16/mulo/weather/message/WeatherMessage.java`
- Test: `src/test/java/com/ktb4/team16/mulo/weather/service/WeatherServiceTest.java`

**Interfaces:**
- Consumes: `WeatherTimePolicy`, `GridCoordinateConverter`, repositories, `KmaWeatherClient`
- Produces: `WeatherService.getWeather(double, double, OffsetDateTime): WeatherResponse`
- Produces: `WeatherCacheWriter.saveDailyForecasts(...)`

- [ ] **Step 1: 캐시 적중 테스트 작성**

```java
@Test
void returnsDailyCacheWithoutCallingKma() {
    when(forecastRepository.findByGridAndCacheDateAndForecastAt(grid, cacheDate, target))
            .thenReturn(Optional.of(cachedForecast));

    WeatherResponse response = service.getWeather(37.5665, 126.9780, requestedAt);

    assertThat(response.data().forecastAt()).isEqualTo(target.atOffset(ZoneOffset.ofHours(9)));
    verifyNoInteractions(kmaWeatherClient);
}
```

- [ ] **Step 2: 당일 묶음에 목표 슬롯이 없을 때 재호출 금지 테스트 작성**

```java
when(forecastRepository.findByGridAndCacheDateAndForecastAt(grid, cacheDate, target))
        .thenReturn(Optional.empty());
when(forecastRepository.existsByGridAndCacheDate(grid, cacheDate)).thenReturn(true);

assertThatThrownBy(() -> service.getWeather(latitude, longitude, requestedAt))
        .isInstanceOf(WeatherApiException.class);
verifyNoInteractions(kmaWeatherClient);
```

- [ ] **Step 3: 최초 적재와 한 단계 fallback 테스트 작성**

첫 후보가 `WeatherApiException`을 던지면 두 번째 후보를 한 번만 호출하고, 성공 응답의 `forecastAt >= target` 슬롯 전체를 writer에 전달하는지 검증한다. 두 후보가 모두 실패하면 세 번째 회차를 호출하지 않는다.

응답 DTO는 API에 허용된 세 필드만 노출한다.

```java
public record WeatherResponse(String message, WeatherData data) {
    public static WeatherResponse from(WeatherGridForecast forecast) {
        return new WeatherResponse(
                WeatherMessage.WEATHER_RETRIEVED.message(),
                new WeatherData(
                        forecast.getForecastAt().atOffset(ZoneOffset.ofHours(9)),
                        forecast.getTemperature(),
                        forecast.getWeatherCondition()));
    }

    public record WeatherData(
            OffsetDateTime forecastAt,
            BigDecimal temperature,
            WeatherCondition weatherCondition) {
    }
}
```

- [ ] **Step 4: 트랜잭션 경계 구현**

`WeatherService`에는 `@Transactional`을 붙이지 않는다. 외부 호출 성공 후 `WeatherCacheWriter`에 저장을 위임한다.

```java
@Transactional
public void saveDailyForecasts(GridCoordinate coordinate, LocalDate cacheDate,
        ZonedDateTime baseAt, ZonedDateTime fetchedAt, List<ForecastSlot> slots) {
    WeatherGrid grid = gridRepository.findByGridXAndGridY(coordinate.x(), coordinate.y())
            .orElseGet(() -> gridRepository.saveAndFlush(WeatherGrid.create(coordinate)));
    forecastRepository.saveAll(slots.stream()
            .map(slot -> WeatherGridForecast.create(
                    grid, cacheDate, slot, baseAt.toLocalDateTime(), fetchedAt.toLocalDateTime()))
            .toList());
    forecastRepository.flush();
}
```

- [ ] **Step 5: Service 조정 로직 구현**

```java
public WeatherResponse getWeather(double latitude, double longitude,
        OffsetDateTime requestedAt) {
    GridCoordinate coordinate = coordinateConverter.convert(latitude, longitude);
    ZonedDateTime target = timePolicy.nearestForecastAt(requestedAt);
    LocalDate cacheDate = requestedAt.atZoneSameInstant(KST).toLocalDate();

    return findCached(coordinate, cacheDate, target)
            .map(WeatherResponse::from)
            .orElseGet(() -> loadOnceAndReturn(coordinate, cacheDate, target));
}
```

`loadOnceAndReturn`은 당일 격자 캐시 존재 여부를 먼저 검사하고, 존재하면 외부 호출 없이 실패한다. 존재하지 않을 때만 최대 두 개의 발표 회차를 시도한다.

- [ ] **Step 6: UNIQUE 경합 복구 테스트 작성**

writer가 `DataIntegrityViolationException`을 던지도록 설정하고, Service가 외부 호출을 반복하지 않은 채 Repository를 한 번 재조회해 다른 요청이 저장한 캐시를 반환하는지 검증한다.

- [ ] **Step 7: Service 테스트 실행**

Run: `./gradlew test --tests '*WeatherServiceTest'`

Expected: 캐시 적중, 당일 재호출 금지, 최초 적재, 한 단계 fallback, 두 회차 실패, UNIQUE 경합 복구 테스트가 PASS한다.

- [ ] **Step 8: 커밋 체크포인트**

자동 커밋하지 않고 실제 이슈 번호와 사용자 승인을 기다린다.

---

### Task 6: 날씨 조회 HTTP API와 공통 오류 변환

**Files:**
- Create: `src/main/java/com/ktb4/team16/mulo/weather/controller/WeatherController.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/error/ErrorCode.java`
- Modify: `src/main/java/com/ktb4/team16/mulo/global/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/ktb4/team16/mulo/weather/controller/WeatherControllerTest.java`
- Modify: `src/test/java/com/ktb4/team16/mulo/global/security/SecurityIntegrationTests.java`

**Interfaces:**
- Produces: `GET /api/weather?latitude={double}&longitude={double}&at={ISO-8601}`
- Produces: `200 WeatherResponse`, `400 INVALID_INPUT_VALUE`, `401 UNAUTHORIZED`, `502 WEATHER_API_ERROR`

- [ ] **Step 1: 성공 Controller 테스트 작성**

```java
mvc.perform(get("/api/weather")
        .param("latitude", "37.5665")
        .param("longitude", "126.9780")
        .param("at", "2026-09-20T11:10:00+09:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("날씨 조회 성공"))
        .andExpect(jsonPath("$.data.forecastAt")
                .value("2026-09-20T11:00:00+09:00"))
        .andExpect(jsonPath("$.data.temperature").value(26.0))
        .andExpect(jsonPath("$.data.weatherCondition").value("CLEAR"));
```

- [ ] **Step 2: Controller와 성공 메시지 구현**

```java
@Validated
@RestController
public class WeatherController {

@GetMapping("/api/weather")
public WeatherResponse getWeather(
        @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime at) {
    return weatherService.getWeather(latitude, longitude, at);
}
}
```

```java
public enum WeatherMessage {
    WEATHER_RETRIEVED("날씨 조회 성공");
}
```

- [ ] **Step 3: 입력 오류 테스트 작성**

`latitude`, `longitude`, `at` 누락, 숫자 형식 오류, ISO-8601 형식 오류, 전 지구 위·경도 범위 초과를 각각 `400 INVALID_INPUT_VALUE`로 검증한다. 국내 기상청 지원 격자 밖 좌표에 대한 별도 오류 코드는 기존 Open Question이므로 이번 구현에서 새 코드를 만들지 않고 변환 실패를 `400 INVALID_INPUT_VALUE`로 통일한다.

- [ ] **Step 4: 기상청 오류 코드와 예외 처리 구현**

```java
WEATHER_API_ERROR(HttpStatus.BAD_GATEWAY,
        "날씨 정보를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
```

```java
@ExceptionHandler(WeatherApiException.class)
public ResponseEntity<ErrorResponse> handleWeatherApi(WeatherApiException exception) {
    return ResponseEntity.status(ErrorCode.WEATHER_API_ERROR.status())
            .body(ErrorResponse.of(ErrorCode.WEATHER_API_ERROR));
}
```

Spring MVC의 누락·타입 변환 예외와 `ConstraintViolationException`은 `INVALID_INPUT_VALUE`로 변환하되 내부 파싱 메시지는 노출하지 않는다.

- [ ] **Step 5: 인증 통합 테스트 작성**

Access Token 없이 `GET /api/weather`를 호출하면 기존 Security 체인이 `401 UNAUTHORIZED`를 반환하는지 검증한다. `GET` 요청이므로 CSRF Cookie와 헤더는 요구하지 않는다.

- [ ] **Step 6: Controller 및 Security 테스트 실행**

Run: `./gradlew test --tests '*WeatherControllerTest' --tests '*SecurityIntegrationTests'`

Expected: 성공, 입력 오류, 502, 비인증 401 테스트가 모두 PASS한다.

- [ ] **Step 7: 커밋 체크포인트**

자동 커밋하지 않고 실제 이슈 번호와 사용자 승인을 기다린다.

---

### Task 7: 전체 검증과 실제 기상청 연동 확인

**Files:**
- Modify only if test evidence finds a defect in Tasks 1-6

**Interfaces:**
- Verifies all prior task interfaces without introducing a new public API

- [ ] **Step 1: 전체 자동 테스트 실행**

Run: `./gradlew clean test`

Expected: 모든 테스트 PASS, Checkstyle 오류 없음, Spring context 로딩 성공.

- [ ] **Step 2: 로컬 MySQL과 애플리케이션 실행**

Run: `docker compose up -d --wait`

Run: `./gradlew bootRun --args='--spring.profiles.active=local'`

Expected: Flyway `V3` 적용, Hibernate schema validation 성공, 애플리케이션 정상 시작.

- [ ] **Step 3: 인증된 실제 API 최초 요청 확인**

Postman에서 Access Token을 포함하고 다음 요청을 전송한다.

```http
GET /api/weather?latitude=37.5665&longitude=126.9780&at=2026-09-20T11:10:00%2B09:00
Authorization: Bearer <Access Token>
```

Expected: `forecastAt=2026-09-20T11:00:00+09:00`인 `200` 응답과 해당 격자의 여러 시간대 DB 행 생성.

- [ ] **Step 4: 같은 날 재요청의 캐시 사용 확인**

같은 격자에 `at=2026-09-20T11:40:00+09:00`을 요청한다.

Expected: `forecastAt=2026-09-20T12:00:00+09:00`을 반환하고, 기상청 외부 호출 및 같은 `cache_date` 행 추가 적재가 발생하지 않는다.

- [ ] **Step 5: DB 저장 결과 확인**

```sql
SELECT wg.grid_x, wg.grid_y, wgf.cache_date, wgf.forecast_at,
       wgf.temperature, wgf.weather_condition, wgf.base_at, wgf.fetched_at
FROM weather_grid_forecasts wgf
JOIN weather_grids wg ON wg.weather_grid_id = wgf.weather_grid_id
WHERE wg.grid_x = 60 AND wg.grid_y = 127
ORDER BY wgf.cache_date, wgf.forecast_at;
```

Expected: 동일 `(grid, cache_date)`의 여러 `forecast_at` 행이 한 발표 회차의 `base_at`과 같은 `fetched_at`을 공유한다.

- [ ] **Step 6: 민감정보와 변경 범위 검토**

Run: `git diff --check`

Run: `git status --short`

Run: `rg -n "KMA_SERVICE_KEY=|serviceKey=[A-Za-z0-9%]" . --glob '!*.md' --glob '!.env'`

Expected: whitespace 오류 없음, `.env` 및 실제 서비스 키 추적 없음, CI 파일 변경 없음.

- [ ] **Step 7: 최종 커밋·push·PR 전 사용자 승인 요청**

구현 파일 목록, 테스트 결과, DB/API 변경, 사용할 실제 이슈 번호와 커밋 메시지 형식을 사용자에게 보여준다. 승인 전에는 커밋, push, PR을 실행하지 않는다.

---

## 구현 순서 요약

```text
1. V3 스키마와 Entity/Repository
2. 가장 가까운 예보시각 및 발표 회차 계산
3. 위·경도 격자 변환과 PTY/SKY 상태 변환
4. 기상청 RestClient와 응답 파싱
5. MySQL 일 단위 캐시 Service
6. GET /api/weather와 오류 응답
7. 전체 테스트 및 실제 API/DB 검증
```

각 단계는 이전 단계의 명시된 인터페이스만 사용한다. 외부 API 클라이언트, 시간 계산, 캐시 저장, HTTP 계층을 분리하여 기상청 응답이나 캐시 정책이 바뀌어도 Controller와 다른 도메인의 변경을 최소화한다.
