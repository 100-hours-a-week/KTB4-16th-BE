package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongRun;
import com.ktb4.team16.mulo.dailysong.service.DailySongQueryService;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.repository.MusicTrackRepository;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(DailySongPersistenceTest.SafeDatabaseCondition.class)
@SpringBootTest(properties = {"mulo.rabbitmq.enabled=false", "mulo.daily-song.scheduler.enabled=false"})
class DailySongDetailIntegrationTest {
    private static final LocalDate MYSQL_DATE_MIN = LocalDate.of(1000, 1, 1);
    private static final LocalDate MYSQL_DATE_MAX = LocalDate.of(9999, 12, 31);
    private static final long MAX_TEST_USER_ID = ChronoUnit.DAYS.between(MYSQL_DATE_MIN, MYSQL_DATE_MAX) + 1;

    @Autowired
    private DailySongQueryService service;

    @Autowired
    private DailySongRunRepository runs;

    @Autowired
    private DailySongJobRepository jobs;

    @Autowired
    private UserRepository users;

    @Autowired
    private MusicTrackRepository tracks;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void returnsCompletedJobTrackOnlyToItsOwner() {
        Fixture fixture = createCompletedJob();
        try {
            var response = service.getDailySong(fixture.userId(), fixture.jobId());

            assertThat(response.data().jobId()).isEqualTo(fixture.jobId());
            assertThat(response.data().track().title()).isEqualTo("테스트 곡");
            assertThat(response.data().track().artistName()).isEqualTo("테스트 가수");
            assertThat(response.data().track().albumImageUrl()).isEqualTo("https://example.test/album");
            assertThat(response.data().track().externalUrl()).isEqualTo("https://example.test/track");
            assertNotFound(fixture.userId() + 1, fixture.jobId());
            assertNotFound(fixture.userId(), fixture.jobId() + 1);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void doesNotExposePendingJob() {
        Fixture fixture = createPendingJob();
        try {
            assertNotFound(fixture.userId(), fixture.jobId());
        } finally {
            deleteFixture(fixture);
        }
    }

    private void assertNotFound(Long userId, Long jobId) {
        assertThatThrownBy(() -> service.getDailySong(userId, jobId))
                .isInstanceOf(NotificationDomainException.class)
                .satisfies(exception -> assertThat(((NotificationDomainException) exception).errorCode())
                        .isEqualTo(ErrorCode.AI_RECOMMENDATION_NOT_FOUND));
    }

    private Fixture createCompletedJob() {
        Fixture fixture = createPendingJob();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE daily_song_runs SET status = 'READY', target_count = 1, finalized_at = ?
                WHERE recommendation_date = ?
                """, fixture.date().atTime(0, 30), fixture.date()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE daily_song_jobs
                SET status = 'COMPLETED', music_track_id = ?, recommend_reason = ?, finished_at = ?
                WHERE job_id = ?
                """, fixture.musicTrackId(), "테스트 추천 이유", fixture.date().atTime(0, 31), fixture.jobId()));
        return fixture;
    }

    private Fixture createPendingJob() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User user = users.saveAndFlush(User.signup(
                    "daily-detail-" + suffix + "@example.test", "test-hash", "dd" + suffix));
            assertThat(user.getUserId()).isBetween(1L, MAX_TEST_USER_ID);
            LocalDate date = MYSQL_DATE_MIN.plusDays(user.getUserId() - 1);
            runs.saveAndFlush(new DailySongRun(date, date.atTime(0, 30)));
            String externalTrackId = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 21);
            MusicTrack track = tracks.saveAndFlush(MusicTrack.create(externalTrackId, "테스트 곡", "테스트 가수",
                    "https://example.test/album", "https://example.test/track", LocalDateTime.now()));
            String eventId = UUID.randomUUID().toString();
            DailySongJob job = jobs.saveAndFlush(new DailySongJob(date, user.getUserId(), eventId));
            return new Fixture(date, user.getUserId(), job.getJobId(), track.getMusicTrackId());
        });
    }

    private void deleteFixture(Fixture fixture) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM daily_song_jobs WHERE job_id = ?", fixture.jobId());
            jdbcTemplate.update("DELETE FROM daily_song_runs WHERE recommendation_date = ?", fixture.date());
            jdbcTemplate.update("DELETE FROM music_tracks WHERE music_track_id = ?", fixture.musicTrackId());
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", fixture.userId());
        });
    }

    private record Fixture(LocalDate date, Long userId, Long jobId, Long musicTrackId) {
    }
}
