package com.ktb4.team16.mulo.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.music.repository.MusicTrackRepository;
import com.ktb4.team16.mulo.place.client.KakaoRegionClient;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.embedding.RecordEmbeddingSubmitter;
import com.ktb4.team16.mulo.upload.entity.Upload;
import com.ktb4.team16.mulo.upload.repository.UploadRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reproduces Place and MusicTrack unique-key races against an isolated MySQL database. */
@SpringBootTest
@ActiveProfiles("weather-it")
@Import(RecordCreateConcurrencyMysqlIntegrationTest.ConcurrencySynchronizationConfiguration.class)
class RecordCreateConcurrencyMysqlIntegrationTest {
    private static final String EXPECTED_DATABASE = "mulo_weather_it";
    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Seoul");
    private static final KakaoRegionClient.LegalRegion LEGAL_REGION =
            new KakaoRegionClient.LegalRegion("1111010100", "종로1가");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordCreationOrchestrator orchestrator;
    @Autowired private UserRepository userRepository;
    @Autowired private UploadRepository uploadRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private MusicTrackRepository musicTrackRepository;
    @Autowired private RaceQueryBarrier raceQueryBarrier;

    @MockitoBean private RecordCreationPreparationService preparationService;
    @MockitoBean private RecordEmbeddingSubmitter embeddingSubmitter;
    @MockitoBean private Clock clock;
    @MockitoBean private Storage storage;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Coordinate> placeCoordinates = new ArrayList<>();
    private final List<String> externalTrackIds = new ArrayList<>();
    private Instant fixedInstant;

    @BeforeEach
    void verifyIsolatedDatabaseAndStubExternalPreparation() throws SQLException {
        assertDatabaseTarget();
        fixedInstant = Instant.now();
        when(clock.instant()).thenReturn(fixedInstant);
        when(clock.getZone()).thenReturn(TEST_ZONE);
        when(preparationService.preparePlace(any(RecordCreateRequest.class)))
                .thenReturn(LEGAL_REGION);
        when(preparationService.prepareWeather(any(RecordCreateRequest.class)))
                .thenReturn(PreparedWeather.withoutWeather());
    }

    @AfterEach
    void removeOnlyRowsCreatedByThisTest() {
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM record_photos WHERE record_id IN "
                    + "(SELECT record_id FROM records WHERE user_id = ?)", userId);
            jdbcTemplate.update("DELETE FROM records WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM uploads WHERE user_id = ?", userId);
        }
        for (Coordinate coordinate : placeCoordinates) {
            jdbcTemplate.update("DELETE FROM places WHERE latitude = ? AND longitude = ?",
                    coordinate.latitude(), coordinate.longitude());
        }
        for (String externalTrackId : externalTrackIds) {
            jdbcTemplate.update("DELETE FROM music_tracks WHERE external_track_id = ?",
                    externalTrackId);
        }
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        userIds.clear();
        placeCoordinates.clear();
        externalTrackIds.clear();
    }

    @RepeatedTest(3)
    void twoConcurrentPlaceMissesRetryAndBothRecordsReuseTheWinningPlace() throws Exception {
        verifyPlaceRace(2);
    }

    @RepeatedTest(3)
    void fiveConcurrentPlaceMissesRetryAndBothRecordsReuseTheWinningPlace() throws Exception {
        verifyPlaceRace(5);
    }

    @RepeatedTest(3)
    void tenConcurrentPlaceMissesRetryAndBothRecordsReuseTheWinningPlace() throws Exception {
        verifyPlaceRace(10);
    }

    @RepeatedTest(3)
    void twoConcurrentMusicTrackMissesRetryAndBothRecordsReuseTheWinningTrack() throws Exception {
        verifyMusicTrackRace(2);
    }

    @RepeatedTest(3)
    void fiveConcurrentMusicTrackMissesRetryAndBothRecordsReuseTheWinningTrack() throws Exception {
        verifyMusicTrackRace(5);
    }

    @RepeatedTest(3)
    void tenConcurrentMusicTrackMissesRetryAndBothRecordsReuseTheWinningTrack() throws Exception {
        verifyMusicTrackRace(10);
    }

    private void verifyPlaceRace(int requestCount) throws Exception {
        Coordinate sharedCoordinate = randomCoordinate();
        placeCoordinates.add(sharedCoordinate);
        List<Fixture> fixtures = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            fixtures.add(createFixture(sharedCoordinate, "track-" + index + "-" + token()));
        }
        assertThat(count("SELECT COUNT(*) FROM places WHERE latitude = ? AND longitude = ?",
                sharedCoordinate)).isZero();

        raceQueryBarrier.armPlaceMisses(requestCount);

        List<Outcome> outcomes = runTogether(fixtures);

        printOutcomes("Place", outcomes, "uk_places_coordinates");
        assertOutcomesAndMetrics("Place", outcomes, requestCount);
        assertThat(raceQueryBarrier.placeMissCount()).isEqualTo(requestCount);
        assertThat(raceQueryBarrier.activePlaceMissCount()).isEqualTo(requestCount);
        long places = count("SELECT COUNT(*) FROM places WHERE latitude = ? AND longitude = ?",
                sharedCoordinate);
        long records = countRecords(fixtures);
        List<Long> placeIds = distinctRecordForeignKeys(fixtures, "place_id");
        long remainingUploads = countUploads(fixtures);
        assertThat(places).isEqualTo(1);
        assertThat(records).isEqualTo(requestCount);
        assertThat(placeIds).hasSize(1);
        assertThat(remainingUploads).isZero();
        verify(preparationService, times(requestCount)).preparePlace(any(RecordCreateRequest.class));
        verify(preparationService, times(requestCount)).prepareWeather(any(RecordCreateRequest.class));
        verify(embeddingSubmitter, times(requestCount)).submit(any());
        System.out.printf("[Place final] requests=%d places=%d records=%d recordPlaceIds=%s "
                + "remainingUploads=%d embeddingSubmissions=%d%n", requestCount, places, records,
                placeIds, remainingUploads, requestCount);
    }

    private void verifyMusicTrackRace(int requestCount) throws Exception {
        String externalTrackId = "race-" + token();
        externalTrackIds.add(externalTrackId);
        List<Fixture> fixtures = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            Coordinate coordinate = randomCoordinate();
            while (placeCoordinates.contains(coordinate)) {
                coordinate = randomCoordinate();
            }
            placeCoordinates.add(coordinate);
            placeRepository.saveAndFlush(new Place(LEGAL_REGION.code(), LEGAL_REGION.name(),
                    coordinate.latitude(), coordinate.longitude()));
            fixtures.add(createFixture(coordinate, externalTrackId));
        }
        assertThat(count("SELECT COUNT(*) FROM music_tracks WHERE external_track_id = ?",
                externalTrackId)).isZero();

        raceQueryBarrier.armMusicTrackMisses(requestCount);

        List<Outcome> outcomes = runTogether(fixtures);

        printOutcomes("MusicTrack", outcomes, "uk_music_tracks_external_track_id");
        assertOutcomesAndMetrics("MusicTrack", outcomes, requestCount);
        assertThat(raceQueryBarrier.musicTrackMissCount()).isEqualTo(requestCount);
        assertThat(raceQueryBarrier.activeMusicTrackMissCount()).isEqualTo(requestCount);
        long tracks = count("SELECT COUNT(*) FROM music_tracks WHERE external_track_id = ?",
                externalTrackId);
        long records = countRecords(fixtures);
        List<Long> trackIds = distinctRecordForeignKeys(fixtures, "music_track_id");
        long remainingUploads = countUploads(fixtures);
        assertThat(tracks).isEqualTo(1);
        assertThat(records).isEqualTo(requestCount);
        assertThat(trackIds).hasSize(1);
        assertThat(remainingUploads).isZero();
        verify(preparationService, times(requestCount)).preparePlace(any(RecordCreateRequest.class));
        verify(preparationService, times(requestCount)).prepareWeather(any(RecordCreateRequest.class));
        verify(embeddingSubmitter, times(requestCount)).submit(any());
        System.out.printf("[MusicTrack final] requests=%d musicTracks=%d records=%d "
                + "recordMusicTrackIds=%s remainingUploads=%d embeddingSubmissions=%d%n",
                requestCount, tracks, records, trackIds, remainingUploads, requestCount);
    }

    private void assertOutcomesAndMetrics(String entityName, List<Outcome> outcomes,
            int requestCount) {
        long successes = outcomes.stream().filter(Outcome::succeeded).count();
        long failures = outcomes.size() - successes;
        // All first reads are synchronized MISSes. A later HIT for this key is the
        // orchestrator's fresh-transaction retry after an allowlisted unique conflict.
        long retries = outcomes.stream().filter(Outcome::retried).count();
        long retrySuccesses = outcomes.stream()
                .filter(outcome -> outcome.retried() && outcome.succeeded()).count();
        long retryFailures = outcomes.stream()
                .filter(outcome -> outcome.retried() && !outcome.succeeded()).count();
        long finalUniqueConflicts = outcomes.stream()
                .filter(outcome -> !outcome.succeeded()
                        && containsConstraint(outcome.failure(), allowedConstraint(entityName)))
                .count();
        long uniqueConflicts = retries + finalUniqueConflicts;
        System.out.printf("[%s concurrency] total=%d success=%d failure=%d uniqueConflicts=%d "
                        + "retries=%d retrySuccess=%d retryFailure=%d retryLimitExhausted=%d "
                        + "poolMax=%d%n",
                entityName, outcomes.size(), successes, failures,
                uniqueConflicts, retries, retrySuccesses, retryFailures, retryFailures,
                hikariMaximumPoolSize());
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(retries).isEqualTo(requestCount - 1);
        assertThat(raceQueryBarrier.retryLookupHitCount()).isEqualTo(retries);
        assertThat(retrySuccesses).isEqualTo(retries);
        assertThat(retryFailures).isZero();
        assertThat(uniqueConflicts).isEqualTo(retries);
    }

    private String allowedConstraint(String entityName) {
        return entityName.equals("Place")
                ? "uk_places_coordinates"
                : "uk_music_tracks_external_track_id";
    }

    private boolean containsConstraint(Throwable failure, String constraintName) {
        if (failure == null) {
            return false;
        }
        boolean integrityViolation = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.dao.DataIntegrityViolationException) {
                integrityViolation = true;
            }
            if (integrityViolation && cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (name != null && name.endsWith(constraintName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int hikariMaximumPoolSize() {
        return ((HikariDataSource) dataSource).getMaximumPoolSize();
    }

    private void printOutcomes(String race, List<Outcome> outcomes, String constraintName) {
        List<String> summary = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            Outcome outcome = outcomes.get(index);
            String result = outcome.succeeded()
                    ? "SUCCESS retried=" + outcome.retried() + " recordId=" + outcome.recordId()
                    : "FAILURE transactionActiveAtCatch=" + outcome.transactionActiveAtCatch()
                            + " retried=" + outcome.retried() + " "
                            + exceptionChain(outcome.failure());
            summary.add("request" + (index + 1) + "=" + result);
        }
        System.out.printf("[%s race] constraint=%s outcomes=%s%n", race, constraintName, summary);
    }

    private String exceptionChain(Throwable failure) {
        List<String> chain = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String item = cause.getClass().getName();
            if (cause instanceof ConstraintViolationException violation) {
                item += "[constraint=" + violation.getConstraintName() + "]";
            }
            if (cause.getMessage() != null) {
                item += ": " + cause.getMessage().split("\\R", 2)[0];
            }
            chain.add(item);
        }
        return String.join(" <- ", chain);
    }

    private List<Outcome> runTogether(List<Fixture> fixtures) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(fixtures.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = fixtures.stream()
                    .map(fixture -> executor.submit(() -> invokeAfterSignal(start, fixture)))
                    .toList();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(90, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Outcome invokeAfterSignal(CountDownLatch start, Fixture fixture) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return Outcome.failure(new IllegalStateException("Timed out waiting for start"),
                        false,
                        TransactionSynchronizationManager.isActualTransactionActive());
            }
            RecordCreateResponse response = orchestrator.createRecord(
                    fixture.userId(), fixture.request());
            return Outcome.success(response.recordId(), raceQueryBarrier.consumeRetryFlag());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Outcome.failure(exception, raceQueryBarrier.consumeRetryFlag(),
                    TransactionSynchronizationManager.isActualTransactionActive());
        } catch (RuntimeException exception) {
            return Outcome.failure(exception, raceQueryBarrier.consumeRetryFlag(),
                    TransactionSynchronizationManager.isActualTransactionActive());
        }
    }

    private Fixture createFixture(Coordinate coordinate, String externalTrackId) {
        String suffix = token();
        User user = userRepository.saveAndFlush(User.signup(
                "record-race-" + suffix + "@example.test", "x".repeat(60), "it" + suffix));
        userIds.add(user.getUserId());
        Upload upload = uploadRepository.saveAndFlush(Upload.create(
                user, "test-only/no-gcs-object-" + suffix + ".jpg", "image/jpeg", 123));
        jdbcTemplate.update("UPDATE uploads SET created_at = ? WHERE upload_id = ?",
                LocalDateTime.ofInstant(fixedInstant, TEST_ZONE), upload.getUploadId());

        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(coordinate.latitude(), coordinate.longitude(),
                        null, null),
                new RecordCreateRequest.Music(externalTrackId, "Test Track", "Test Artist",
                        "https://example.test/album.jpg", "https://example.test/track"),
                10, "concurrency test", upload.getUploadId());
        return new Fixture(user.getUserId(), upload.getUploadId(), request);
    }

    private void assertDatabaseTarget() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo(EXPECTED_DATABASE);
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:mysql://127.0.0.1:3307/" + EXPECTED_DATABASE);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                    .isEqualTo(6);
        }
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(hikariMaximumPoolSize())
                .as("all synchronized requests must acquire a connection before the barrier releases")
                .isGreaterThanOrEqualTo(10);
    }

    private Coordinate randomCoordinate() {
        long offset = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 900_000L) + 100_000L;
        BigDecimal latitude = new BigDecimal("37.5000000")
                .add(new BigDecimal("0.0000001").multiply(BigDecimal.valueOf(offset)));
        BigDecimal longitude = new BigDecimal("127.0000000");
        return new Coordinate(latitude, longitude);
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private long count(String sql, Coordinate coordinate) {
        return jdbcTemplate.queryForObject(sql, Long.class,
                coordinate.latitude(), coordinate.longitude());
    }

    private long count(String sql, String value) {
        return jdbcTemplate.queryForObject(sql, Long.class, value);
    }

    private long countRecords(List<Fixture> fixtures) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM records WHERE user_id IN ("
                + placeholders(fixtures.size()) + ")", Long.class, userIdArguments(fixtures));
    }

    private long countUploads(List<Fixture> fixtures) {
        Object[] uploadIds = fixtures.stream().map(Fixture::uploadId).toArray();
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uploads WHERE upload_id IN ("
                + placeholders(fixtures.size()) + ")", Long.class, uploadIds);
    }

    private List<Long> distinctRecordForeignKeys(
            List<Fixture> fixtures, String foreignKeyColumn) {
        if (!List.of("place_id", "music_track_id").contains(foreignKeyColumn)) {
            throw new IllegalArgumentException("Unexpected foreign key column");
        }
        return jdbcTemplate.queryForList("SELECT DISTINCT " + foreignKeyColumn
                        + " FROM records WHERE user_id IN (" + placeholders(fixtures.size()) + ")",
                Long.class, userIdArguments(fixtures));
    }

    private Object[] userIdArguments(List<Fixture> fixtures) {
        return fixtures.stream().map(Fixture::userId).toArray();
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencySynchronizationConfiguration {
        @Bean
        RaceQueryBarrier raceQueryBarrier() {
            return new RaceQueryBarrier();
        }

        @Bean
        static BeanPostProcessor raceQueryBarrierPostProcessor(RaceQueryBarrier barrier) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName)
                        throws BeansException {
                    boolean placeRepositoryBean = bean instanceof PlaceRepository;
                    boolean musicRepositoryBean = bean instanceof MusicTrackRepository;
                    if (!placeRepositoryBean && !musicRepositoryBean) {
                        return bean;
                    }

                    ProxyFactory proxyFactory = new ProxyFactory(bean);
                    proxyFactory.addAdvice((MethodInterceptor) invocation -> {
                        Object result = invocation.proceed();
                        if (result instanceof Optional<?> optional && optional.isEmpty()) {
                            String methodName = invocation.getMethod().getName();
                            if (placeRepositoryBean
                                    && methodName.equals("findByLatitudeAndLongitude")) {
                                barrier.afterPlaceQuery(true);
                            } else if (musicRepositoryBean
                                    && methodName.equals("findByExternalTrackId")) {
                                barrier.afterMusicTrackQuery(true);
                            }
                        } else if (result instanceof Optional<?> optional && optional.isPresent()) {
                            String methodName = invocation.getMethod().getName();
                            if (placeRepositoryBean
                                    && methodName.equals("findByLatitudeAndLongitude")) {
                                barrier.afterPlaceQuery(false);
                            } else if (musicRepositoryBean
                                    && methodName.equals("findByExternalTrackId")) {
                                barrier.afterMusicTrackQuery(false);
                            }
                        }
                        return result;
                    });
                    return proxyFactory.getProxy(bean.getClass().getClassLoader());
                }
            };
        }
    }

    static class RaceQueryBarrier {
        private enum RaceKind {PLACE, MUSIC_TRACK}

        private final ThreadLocal<Boolean> requestRetried = ThreadLocal.withInitial(() -> false);
        private final AtomicInteger placeMissCount = new AtomicInteger();
        private final AtomicInteger musicTrackMissCount = new AtomicInteger();
        private final AtomicInteger activePlaceMissCount = new AtomicInteger();
        private final AtomicInteger activeMusicTrackMissCount = new AtomicInteger();
        private final AtomicInteger placeHitCount = new AtomicInteger();
        private final AtomicInteger musicTrackHitCount = new AtomicInteger();
        private volatile RaceKind raceKind;
        private volatile CountDownLatch placeMisses;
        private volatile CountDownLatch musicTrackMisses;

        void armPlaceMisses(int requestCount) {
            reset(RaceKind.PLACE);
            placeMisses = new CountDownLatch(requestCount);
        }

        void armMusicTrackMisses(int requestCount) {
            reset(RaceKind.MUSIC_TRACK);
            musicTrackMisses = new CountDownLatch(requestCount);
        }

        private void reset(RaceKind kind) {
            raceKind = kind;
            placeMissCount.set(0);
            musicTrackMissCount.set(0);
            activePlaceMissCount.set(0);
            activeMusicTrackMissCount.set(0);
            placeHitCount.set(0);
            musicTrackHitCount.set(0);
        }

        void afterPlaceQuery(boolean miss) {
            if (raceKind != RaceKind.PLACE) {
                return;
            }
            if (!miss) {
                placeHitCount.incrementAndGet();
                requestRetried.set(true);
                return;
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                activePlaceMissCount.incrementAndGet();
            }
            await(placeMisses, placeMissCount, "Place");
        }

        void afterMusicTrackQuery(boolean miss) {
            if (raceKind != RaceKind.MUSIC_TRACK) {
                return;
            }
            if (!miss) {
                musicTrackHitCount.incrementAndGet();
                requestRetried.set(true);
                return;
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                activeMusicTrackMissCount.incrementAndGet();
            }
            await(musicTrackMisses, musicTrackMissCount, "MusicTrack");
        }

        int placeMissCount() {
            return placeMissCount.get();
        }

        int musicTrackMissCount() {
            return musicTrackMissCount.get();
        }

        int activePlaceMissCount() {
            return activePlaceMissCount.get();
        }

        int activeMusicTrackMissCount() {
            return activeMusicTrackMissCount.get();
        }

        int retryLookupHitCount() {
            return raceKind == RaceKind.PLACE ? placeHitCount.get() : musicTrackHitCount.get();
        }

        boolean consumeRetryFlag() {
            boolean retried = requestRetried.get();
            requestRetried.remove();
            return retried;
        }

        private void await(CountDownLatch barrier, AtomicInteger count, String entityName) {
            if (barrier == null) {
                return;
            }
            count.incrementAndGet();
            barrier.countDown();
            try {
                if (!barrier.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for concurrent " + entityName + " misses");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for concurrent " + entityName + " misses",
                        exception);
            }
        }
    }

    private record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }

    private record Fixture(Long userId, Long uploadId, RecordCreateRequest request) {
    }

    private record Outcome(boolean succeeded, Long recordId, Throwable failure,
            boolean retried, boolean transactionActiveAtCatch) {
        private static Outcome success(Long recordId, boolean retried) {
            return new Outcome(true, recordId, null, retried, false);
        }

        private static Outcome failure(Throwable failure, boolean retried,
                boolean transactionActiveAtCatch) {
            return new Outcome(false, null, failure, retried, transactionActiveAtCatch);
        }
    }
}
