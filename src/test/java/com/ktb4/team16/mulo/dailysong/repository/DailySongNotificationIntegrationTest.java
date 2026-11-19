package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongRun;
import com.ktb4.team16.mulo.dailysong.service.DailySongNotificationBatchService;
import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.music.repository.MusicTrackRepository;
import com.ktb4.team16.mulo.notification.entity.NotificationType;
import com.ktb4.team16.mulo.notification.service.NotificationService;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(DailySongPersistenceTest.SafeDatabaseCondition.class)
@SpringBootTest(properties = {"mulo.rabbitmq.enabled=false", "mulo.daily-song.scheduler.enabled=false"})
class DailySongNotificationIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate MYSQL_DATE_MIN = LocalDate.of(1000, 1, 1);
    private static final long MAX_TEST_USER_ID = ChronoUnit.DAYS.between(
            MYSQL_DATE_MIN, LocalDate.of(9999, 12, 31)) + 1;

    @Autowired private DailySongNotificationBatchService batches;
    @Autowired private DailySongRunRepository runs;
    @Autowired private DailySongJobRepository jobs;
    @Autowired private UserRepository users;
    @Autowired private MusicTrackRepository tracks;
    @Autowired private NotificationService notificationService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private NotificationCreatedNotifier notifier;
    @MockitoBean private Clock clock;

    private final AtomicReference<Instant> currentTime = new AtomicReference<>();

    @BeforeEach
    void setClock() {
        when(clock.instant()).thenAnswer(invocation -> currentTime.get());
        when(clock.getZone()).thenReturn(SEOUL);
    }

    @Test
    void publishesAtSevenAndDoesNotRecreateDeletedNotification() {
        Fixture fixture = createCompletedJob();
        try {
            assumeNoOtherDueJobs(fixture);
            setTime(fixture.date(), LocalTime.of(6, 59, 59));
            assertThat(batches.createNextBatch()).isZero();

            setTime(fixture.date(), LocalTime.of(7, 0));
            assertThat(batches.createNextBatch()).isEqualTo(1);
            verify(notifier).notificationCreated(Set.of(fixture.userId()));

            var notifications = notificationService.getNotifications(fixture.userId(), null, null)
                    .data().notifications();
            assertThat(notifications).hasSize(1);
            var notification = notifications.getFirst();
            assertThat(notification.type()).isEqualTo(NotificationType.DAILY_SONG_READY);
            assertThat(notification.targetId()).isEqualTo(fixture.jobId());
            assertThat(notification.targetType()).isEqualTo("DAILY_SONG");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT notified_at FROM daily_song_jobs WHERE job_id = ?
                    """, LocalDateTime.class, fixture.jobId())).isNotNull();

            notificationService.delete(fixture.userId(), notification.notificationId());
            assertThat(batches.createNextBatch()).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications WHERE source_event_id = ?
                    """, Integer.class, fixture.eventId())).isZero();
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void skipsCompletedJobAfterUserWithdrawal() {
        Fixture fixture = createCompletedJob();
        try {
            assumeNoOtherDueJobs(fixture);
            jdbcTemplate.update("UPDATE users SET deleted_at = ? WHERE user_id = ?",
                    fixture.date().atTime(6, 0), fixture.userId());
            setTime(fixture.date(), LocalTime.of(7, 0));

            assertThat(batches.createNextBatch()).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications WHERE source_event_id = ?
                    """, Integer.class, fixture.eventId())).isZero();
        } finally {
            deleteFixture(fixture);
        }
    }

    private Fixture createCompletedJob() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User user = users.saveAndFlush(User.signup(
                    "daily-" + suffix + "@example.test", "test-hash", "ds" + suffix));
            assertThat(user.getUserId()).isBetween(1L, MAX_TEST_USER_ID);
            LocalDate date = MYSQL_DATE_MIN.plusDays(user.getUserId() - 1);
            runs.saveAndFlush(new DailySongRun(date, date.atTime(0, 30)));
            String trackId = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 21);
            MusicTrack track = tracks.saveAndFlush(MusicTrack.create(trackId, "테스트 곡", "테스트 가수",
                    "https://example.test/album", "https://example.test/track", LocalDateTime.now()));
            String eventId = UUID.randomUUID().toString();
            DailySongJob job = jobs.saveAndFlush(new DailySongJob(date, user.getUserId(), eventId));
            jdbcTemplate.update("""
                    UPDATE daily_song_runs SET status = 'READY', target_count = 1, finalized_at = ?
                    WHERE recommendation_date = ?
                    """, date.atTime(0, 30), date);
            jdbcTemplate.update("""
                    UPDATE daily_song_jobs
                    SET status = 'COMPLETED', music_track_id = ?, recommend_reason = ?, finished_at = ?
                    WHERE job_id = ?
                    """, track.getMusicTrackId(), "테스트 추천 이유", date.atTime(0, 31), job.getJobId());
            return new Fixture(date, user.getUserId(), job.getJobId(), track.getMusicTrackId(), eventId);
        });
    }

    private void assumeNoOtherDueJobs(Fixture fixture) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM daily_song_jobs j JOIN users u ON u.user_id = j.user_id
                WHERE j.recommendation_date <= ? AND j.job_id <> ? AND j.status = 'COMPLETED'
                    AND j.notified_at IS NULL AND u.deleted_at IS NULL
                """, Long.class, fixture.date(), fixture.jobId());
        assumeTrue(count != null && count == 0);
    }

    private void setTime(LocalDate date, LocalTime time) {
        currentTime.set(date.atTime(time).atZone(SEOUL).toInstant());
    }

    private void deleteFixture(Fixture fixture) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM notifications WHERE source_event_id = ?", fixture.eventId());
            jdbcTemplate.update("DELETE FROM daily_song_jobs WHERE job_id = ?", fixture.jobId());
            jdbcTemplate.update("DELETE FROM daily_song_runs WHERE recommendation_date = ?", fixture.date());
            jdbcTemplate.update("DELETE FROM music_tracks WHERE music_track_id = ?", fixture.musicTrackId());
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", fixture.userId());
        });
    }

    private record Fixture(LocalDate date, Long userId, Long jobId, Long musicTrackId, String eventId) {
    }
}
