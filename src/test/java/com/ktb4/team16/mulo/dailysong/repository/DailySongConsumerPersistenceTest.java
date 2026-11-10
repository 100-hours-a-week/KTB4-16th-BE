package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongRun;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobResultService;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobResultService.CompletionOutcome;
import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.repository.MusicTrackRepository;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.place.repository.PlaceRepository;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.record.entity.Record;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(DailySongPersistenceTest.SafeDatabaseCondition.class)
@SpringBootTest(properties = "mulo.rabbitmq.enabled=false")
class DailySongConsumerPersistenceTest {
    @Autowired private DailySongRunRepository runs;
    @Autowired private DailySongJobRepository jobs;
    @Autowired private DailySongJobClaimRepository claims;
    @Autowired private DailySongJobResultService results;
    @Autowired private UserRepository users;
    @Autowired private PlaceRepository places;
    @Autowired private RecordRepository records;
    @Autowired private MusicTrackRepository musicTracks;
    @Autowired private DailySongRecordLocationRepository locations;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @Test
    void storesOneTrackAndCompletesOnlyForClaimOwner() {
        Fixture fixture = insertFixture();
        String trackId = "ds" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Claim owner = claims.claim(fixture.jobId(), now, now.plusMinutes(5)).orElseThrow();
            assertThat(claims.claim(fixture.jobId(), now, now.plusMinutes(5))).isEmpty();
            assertThat(claims.markAiCallStarted(owner, now.plusSeconds(1))).isTrue();

            RecommendedTrack track = new RecommendedTrack(trackId, "테스트 곡", "테스트 가수",
                    "https://example.test/album", "https://example.test/track");
            Claim wrongOwner = new Claim(owner.jobId(), owner.userId(), owner.recommendationDate(),
                    owner.targetCutoffAt(), UUID.randomUUID().toString());
            assertThat(results.complete(wrongOwner, track, "추천 이유")).isEqualTo(CompletionOutcome.NOT_APPLIED);
            assertThat(results.complete(owner, track, "추천 이유")).isEqualTo(CompletionOutcome.COMPLETED);
            assertThat(results.complete(owner, track, "추천 이유")).isEqualTo(CompletionOutcome.NOT_APPLIED);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_jobs WHERE job_id = ?
                    """, String.class, fixture.jobId())).isEqualTo("COMPLETED");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT recommend_reason FROM daily_song_jobs WHERE job_id = ?
                    """, String.class, fixture.jobId())).isEqualTo("추천 이유");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM music_tracks WHERE external_track_id = ?
                    """, Integer.class, trackId)).isEqualTo(1);
        } finally {
            deleteFixture(fixture, trackId);
        }
    }

    @Test
    void concurrentConsumersCannotClaimSameJob() throws Exception {
        Fixture fixture = insertFixture();
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            LocalDateTime now = LocalDateTime.now(clock);
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return claims.claim(fixture.jobId(), now, now.plusMinutes(5)).isPresent();
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return claims.claim(fixture.jobId(), now, now.plusMinutes(5)).isPresent();
            });
            start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        } finally {
            deleteFixture(fixture, null);
        }
    }

    @Test
    void expiredClaimIsFailedWithoutCallingAiAgain() {
        Fixture fixture = insertFixture();
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Claim owner = claims.claim(fixture.jobId(), now, now.plusSeconds(1)).orElseThrow();
            assertThat(claims.recoverExpired(fixture.jobId(), now.plusSeconds(2))).isEqualTo(1);
            assertThat(claims.claim(fixture.jobId(), now.plusSeconds(3), now.plusMinutes(5))).isEmpty();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_jobs WHERE job_id = ?
                    """, String.class, fixture.jobId())).isEqualTo("FAILED");
            assertThat(claims.markAiCallStarted(owner, now.plusSeconds(3))).isFalse();
            RecommendedTrack lateTrack = new RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "늦은 곡",
                    "테스트 가수", "https://example.test/album", "https://example.test/track");
            assertThat(results.complete(owner, lateTrack, "늦은 응답")).isEqualTo(CompletionOutcome.NOT_APPLIED);
        } finally {
            deleteFixture(fixture, null);
        }
    }

    @Test
    void trackInsertFailureDoesNotCompleteJob() {
        Fixture fixture = insertFixture();
        String trackId = "ds" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Claim owner = claims.claim(fixture.jobId(), now, now.plusMinutes(5)).orElseThrow();
            RecommendedTrack invalidTrack = new RecommendedTrack(trackId, "곡".repeat(300),
                    "테스트 가수", "https://example.test/album", "https://example.test/track");

            assertThatThrownBy(() -> results.complete(owner, invalidTrack, "추천 이유"))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_jobs WHERE job_id = ?
                    """, String.class, fixture.jobId())).isEqualTo("PROCESSING");
        } finally {
            deleteFixture(fixture, trackId);
        }
    }

    @Test
    void withdrawnUserIsCancelledBeforeAiCall() {
        Fixture fixture = insertFixture();
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Claim owner = claims.claim(fixture.jobId(), now, now.plusMinutes(5)).orElseThrow();
            User user = users.findById(fixture.userId()).orElseThrow();
            user.softDelete(now);
            users.saveAndFlush(user);

            assertThat(claims.markAiCallStarted(owner, now.plusSeconds(1))).isFalse();
            assertThat(claims.fail(owner, "CLAIM_NOT_AVAILABLE", now.plusSeconds(1))).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_jobs WHERE job_id = ?
                    """, String.class, fixture.jobId())).isEqualTo("CANCELLED");
        } finally {
            deleteFixture(fixture, null);
        }
    }

    @Test
    void latestLocationExcludesDeletedAndAfterCutoffRecordsWithStableTieBreak() {
        Fixture fixture = insertFixture();
        String trackId = "ds" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long firstPlaceId = null;
        Long secondPlaceId = null;
        Long laterPlaceId = null;
        Long deletedPlaceId = null;
        try {
            User user = users.findById(fixture.userId()).orElseThrow();
            MusicTrack track = musicTracks.saveAndFlush(MusicTrack.create(trackId, "테스트 곡",
                    "테스트 가수", "https://example.test/album", "https://example.test/track",
                    LocalDateTime.now(clock)));
            Place first = places.saveAndFlush(new Place(null, null,
                    new BigDecimal("37.1000"), new BigDecimal("127.1000")));
            Place second = places.saveAndFlush(new Place(null, null,
                    new BigDecimal("37.2000"), new BigDecimal("127.2000")));
            Place later = places.saveAndFlush(new Place(null, null,
                    new BigDecimal("37.3000"), new BigDecimal("127.3000")));
            Place deleted = places.saveAndFlush(new Place(null, null,
                    new BigDecimal("37.4000"), new BigDecimal("127.4000")));
            firstPlaceId = first.getPlaceId();
            secondPlaceId = second.getPlaceId();
            laterPlaceId = later.getPlaceId();
            deletedPlaceId = deleted.getPlaceId();
            LocalDateTime cutoff = fixture.date().atTime(0, 30);
            records.saveAndFlush(Record.create(user, first, track, Record.WeatherCondition.CLEAR,
                    BigDecimal.valueOf(20), (byte) 3, null, cutoff.minusMinutes(1)));
            records.saveAndFlush(Record.create(user, second, track, Record.WeatherCondition.CLEAR,
                    BigDecimal.valueOf(20), (byte) 3, null, cutoff.minusMinutes(1)));
            records.saveAndFlush(Record.create(user, later, track, Record.WeatherCondition.CLEAR,
                    BigDecimal.valueOf(20), (byte) 3, null, cutoff.plusSeconds(1)));
            Record removed = records.saveAndFlush(Record.create(user, deleted, track,
                    Record.WeatherCondition.CLEAR, BigDecimal.valueOf(20), (byte) 3, null, cutoff));
            removed.softDelete(cutoff.plusMinutes(1));
            records.saveAndFlush(removed);

            assertThat(locations.findLatestValid(fixture.userId(), cutoff)).hasValueSatisfying(coordinates -> {
                assertThat(coordinates.latitude()).isEqualByComparingTo("37.2000");
                assertThat(coordinates.longitude()).isEqualByComparingTo("127.2000");
            });
        } finally {
            jdbcTemplate.update("DELETE FROM records WHERE user_id = ?", fixture.userId());
            deleteFixture(fixture, trackId);
            for (Long placeId : new Long[] {firstPlaceId, secondPlaceId, laterPlaceId, deletedPlaceId}) {
                if (placeId != null) {
                    jdbcTemplate.update("DELETE FROM places WHERE place_id = ?", placeId);
                }
            }
        }
    }

    private Fixture insertFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User user = users.saveAndFlush(User.signup("daily-consumer-" + suffix + "@example.test",
                    "test-hash", "dc" + suffix));
            LocalDate date = LocalDate.of(1000, 1, 1).plusDays(user.getUserId() - 1);
            runs.saveAndFlush(new DailySongRun(date, date.atTime(0, 30)));
            DailySongJob job = jobs.saveAndFlush(new DailySongJob(date, user.getUserId(),
                    UUID.randomUUID().toString()));
            return new Fixture(job.getJobId(), user.getUserId(), date);
        });
    }

    private void deleteFixture(Fixture fixture, String trackId) {
        jdbcTemplate.update("DELETE FROM daily_song_jobs WHERE job_id = ?", fixture.jobId());
        jdbcTemplate.update("DELETE FROM daily_song_runs WHERE recommendation_date = ?", fixture.date());
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", fixture.userId());
        if (trackId != null) {
            jdbcTemplate.update("DELETE FROM music_tracks WHERE external_track_id = ?", trackId);
        }
    }

    private record Fixture(long jobId, long userId, LocalDate date) {
    }
}
