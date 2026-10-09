package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSnapshot;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.repository.UploadRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.weather.client.KmaWeatherClient;
import com.ktb4.team16.mulo.weather.domain.ForecastSlot;
import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import com.ktb4.team16.mulo.weather.entity.WeatherGridForecast;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import com.ktb4.team16.mulo.weather.repository.WeatherGridForecastRepository;
import com.ktb4.team16.mulo.weather.repository.WeatherGridRepository;
import com.ktb4.team16.mulo.weather.service.GridCoordinateConverter;
import com.ktb4.team16.mulo.weather.service.WeatherTimePolicy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("weather-it")
class RecordCreationWeatherMysqlIntegrationTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String EXPECTED_DATABASE = "mulo_weather_it";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordCreationOrchestrator orchestrator;
    @Autowired private RecordService recordService;
    @Autowired private UserRepository userRepository;
    @Autowired private UploadRepository uploadRepository;
    @Autowired private WeatherGridRepository weatherGridRepository;
    @Autowired private WeatherGridForecastRepository forecastRepository;
    @Autowired private GridCoordinateConverter coordinateConverter;
    @Autowired private WeatherTimePolicy weatherTimePolicy;
    @MockitoBean private Clock clock;
    @MockitoBean private KakaoRegionClient kakaoRegionClient;
    @MockitoBean private KmaWeatherClient kmaWeatherClient;
    @MockitoBean private RecordEmbeddingSubmitter embeddingSubmitter;
    @MockitoBean private Storage storage;

    private Instant fixedInstant;
    private Fixture fixture;
    private WeatherKey weatherKey;

    @BeforeEach
    void verifyDatabaseAndStubAllExternalClients() throws SQLException {
        assertDatabaseTarget();
        assertThat(AopUtils.isAopProxy(recordService)).isTrue();

        fixedInstant = ZonedDateTime.now(KST).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .plusMinutes(10).toInstant();
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(KST);

        when(kakaoRegionClient.findLegalRegion(any(BigDecimal.class), any(BigDecimal.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    return new KakaoRegionClient.LegalRegion("1111010100", "종로1가");
                });
        when(kmaWeatherClient.fetch(any(GridCoordinate.class), any(ZonedDateTime.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    return List.of(new ForecastSlot(targetAt(), new BigDecimal("23.4"),
                            WeatherCondition.CLEAR));
                });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            RecordEmbeddingSnapshot snapshot = invocation.getArgument(0);
            assertThat(recordCount(snapshot.userId())).isEqualTo(1);
            return null;
        }).when(embeddingSubmitter).submit(any(RecordEmbeddingSnapshot.class));
    }

    @AfterEach
    void removeOnlyRowsCreatedByThisTest() {
        if (fixture != null) {
            Long userId = fixture.user().getUserId();
            jdbcTemplate.update("DELETE FROM record_photos WHERE record_id IN "
                    + "(SELECT record_id FROM records WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM records WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM uploads WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM places WHERE latitude = ? AND longitude = ?",
                    fixture.request().location().latitude(),
                    fixture.request().location().longitude());
            jdbcTemplate.update("DELETE FROM music_tracks WHERE external_track_id = ?",
                    fixture.request().music().externalTrackId());
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        if (weatherKey != null) {
            weatherGridRepository.findByGridXAndGridY(weatherKey.grid().x(), weatherKey.grid().y())
                    .ifPresent(grid -> {
                        jdbcTemplate.update("DELETE FROM weather_grid_forecasts "
                                        + "WHERE weather_grid_id = ?",
                                grid.getWeatherGridId());
                        weatherGridRepository.delete(grid);
                    });
        }
        fixture = null;
        weatherKey = null;
    }

    @Test
    void cacheHitCreatesRecordWithoutCallingKmaAndCommitsBeforeEmbedding() {
        fixture = createFixture(1, "valid comment");
        weatherKey = weatherKeyFor(fixture.request());
        seedWeatherCache(weatherKey);

        orchestrator.createRecord(fixture.user().getUserId(), fixture.request());

        assertCommittedRecord(fixture, WeatherCondition.CLEAR, new BigDecimal("23.4"));
        verify(kmaWeatherClient, never()).fetch(any(GridCoordinate.class), any(ZonedDateTime.class));
        verify(embeddingSubmitter).submit(any(RecordEmbeddingSnapshot.class));
    }

    @Test
    void cacheMissCallsKmaAndCommitsCacheAndRecord() {
        fixture = createFixture(2, "valid comment");
        weatherKey = weatherKeyFor(fixture.request());

        orchestrator.createRecord(fixture.user().getUserId(), fixture.request());

        assertCommittedRecord(fixture, WeatherCondition.CLEAR, new BigDecimal("23.4"));
        assertThat(forecastCount(weatherKey)).isEqualTo(1);
        verify(kmaWeatherClient).fetch(any(GridCoordinate.class), any(ZonedDateTime.class));
        verify(embeddingSubmitter).submit(any(RecordEmbeddingSnapshot.class));
    }

    @Test
    void cacheMissRemainsCommittedWhenRecordTransactionRollsBack() {
        fixture = createFixture(3, "x".repeat(81));
        weatherKey = weatherKeyFor(fixture.request());

        assertThatThrownBy(() -> orchestrator.createRecord(
                fixture.user().getUserId(), fixture.request())).isInstanceOf(RuntimeException.class);

        assertThat(recordCount(fixture.user().getUserId())).isZero();
        assertThat(photoCount(fixture.user().getUserId())).isZero();
        assertThat(placeCount(fixture.request())).isZero();
        assertThat(uploadCount(fixture.upload().getUploadId())).isEqualTo(1);
        assertThat(forecastCount(weatherKey)).isEqualTo(1);
        verify(kakaoRegionClient).findLegalRegion(any(BigDecimal.class), any(BigDecimal.class));
        verify(embeddingSubmitter, never()).submit(any(RecordEmbeddingSnapshot.class));
    }

    @Test
    void weatherApiFailureContinuesWithoutWeatherAndDoesNotRollbackRecord() {
        fixture = createFixture(4, "valid comment");
        weatherKey = weatherKeyFor(fixture.request());
        doThrow(new WeatherApiException()).when(kmaWeatherClient)
                .fetch(any(GridCoordinate.class), any(ZonedDateTime.class));

        orchestrator.createRecord(fixture.user().getUserId(), fixture.request());

        assertCommittedRecord(fixture, null, null);
        assertThat(forecastCount(weatherKey)).isZero();
        verify(kmaWeatherClient, atLeastOnce())
                .fetch(any(GridCoordinate.class), any(ZonedDateTime.class));
        verify(embeddingSubmitter).submit(any(RecordEmbeddingSnapshot.class));
    }

    private void assertDatabaseTarget() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo(EXPECTED_DATABASE);
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:mysql://127.0.0.1:3307/" + EXPECTED_DATABASE);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                    .isEqualTo(5);
        }
    }

    private Fixture createFixture(int index, String comment) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User user = userRepository.saveAndFlush(User.signup(
                "weather-it-" + suffix + "@example.test", "x".repeat(60), "it" + suffix));
        Upload upload = uploadRepository.saveAndFlush(Upload.create(
                user, "test-only/no-gcs-object-" + suffix + ".jpg", "image/jpeg", 123));
        jdbcTemplate.update("UPDATE uploads SET created_at = ? WHERE upload_id = ?",
                LocalDateTime.now(clock), upload.getUploadId());
        BigDecimal latitude = new BigDecimal("37.5665000")
                .add(new BigDecimal("0.0001000").multiply(BigDecimal.valueOf(index)));
        BigDecimal longitude = new BigDecimal("126.9780000");
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(latitude, longitude, null, null),
                new RecordCreateRequest.Music("it" + suffix, "Test Track", "Test Artist",
                        "https://example.test/album.jpg", "https://example.test/track"),
                10, comment, upload.getUploadId());
        return new Fixture(user, upload, request);
    }

    private WeatherKey weatherKeyFor(RecordCreateRequest request) {
        GridCoordinate grid = coordinateConverter.convert(
                request.location().latitude().doubleValue(),
                request.location().longitude().doubleValue());
        LocalDate cacheDate = fixedTime().toLocalDate();
        return new WeatherKey(grid, cacheDate, targetAt());
    }

    private void seedWeatherCache(WeatherKey key) {
        WeatherGrid grid = weatherGridRepository.saveAndFlush(WeatherGrid.create(key.grid()));
        ForecastSlot slot = new ForecastSlot(key.forecastAt(), new BigDecimal("23.4"),
                WeatherCondition.CLEAR);
        forecastRepository.saveAndFlush(WeatherGridForecast.create(
                grid, key.cacheDate(), slot, fixedTime().minusHours(3), fixedTime()));
    }

    private void assertCommittedRecord(Fixture created, WeatherCondition condition,
            BigDecimal temperature) {
        assertThat(recordCount(created.user().getUserId())).isEqualTo(1);
        assertThat(photoCount(created.user().getUserId())).isEqualTo(1);
        assertThat(placeCount(created.request())).isEqualTo(1);
        assertThat(uploadCount(created.upload().getUploadId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT legal_dong_code FROM places "
                        + "WHERE latitude = ? AND longitude = ?", String.class,
                created.request().location().latitude(), created.request().location().longitude()))
                .isEqualTo("1111010100");
        verify(kakaoRegionClient).findLegalRegion(any(BigDecimal.class), any(BigDecimal.class));
        assertThat(jdbcTemplate.queryForObject("SELECT weather_condition FROM records "
                        + "WHERE user_id = ?", String.class, created.user().getUserId()))
                .isEqualTo(condition == null ? null : condition.name());
        BigDecimal storedTemperature = jdbcTemplate.queryForObject("SELECT temperature FROM records "
                + "WHERE user_id = ?", BigDecimal.class, created.user().getUserId());
        if (temperature == null) {
            assertThat(storedTemperature).isNull();
        } else {
            assertThat(storedTemperature).isEqualByComparingTo(temperature);
        }
    }

    private long recordCount(Long userId) {
        return count("SELECT COUNT(*) FROM records WHERE user_id = ?", userId);
    }

    private long photoCount(Long userId) {
        return count("SELECT COUNT(*) FROM record_photos p JOIN records r "
                + "ON p.record_id = r.record_id WHERE r.user_id = ?", userId);
    }

    private long placeCount(RecordCreateRequest request) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM places "
                        + "WHERE latitude = ? AND longitude = ?", Long.class,
                request.location().latitude(), request.location().longitude());
    }

    private long uploadCount(Long uploadId) {
        return count("SELECT COUNT(*) FROM uploads WHERE upload_id = ?", uploadId);
    }

    private long forecastCount(WeatherKey key) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM weather_grid_forecasts f "
                        + "JOIN weather_grids g ON g.weather_grid_id = f.weather_grid_id "
                        + "WHERE g.grid_x = ? AND g.grid_y = ? AND f.cache_date = ? "
                        + "AND f.forecast_at = ?", Long.class, key.grid().x(), key.grid().y(),
                key.cacheDate(), key.forecastAt());
    }

    private long count(String sql, Long value) {
        return jdbcTemplate.queryForObject(sql, Long.class, value);
    }

    private LocalDateTime fixedTime() {
        return LocalDateTime.ofInstant(fixedInstant, KST);
    }

    private LocalDateTime targetAt() {
        return weatherTimePolicy.nearestForecastAt(OffsetDateTime.ofInstant(fixedInstant, KST))
                .toLocalDateTime();
    }

    private record Fixture(User user, Upload upload, RecordCreateRequest request) {
    }

    private record WeatherKey(GridCoordinate grid, LocalDate cacheDate,
            LocalDateTime forecastAt) {
    }
}
