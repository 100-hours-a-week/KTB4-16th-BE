package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.dailysong.entity.DailySongDispatchOutbox;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongRun;
import com.ktb4.team16.mulo.dailysong.repository.DailySongOutboxClaimRepository.Claim;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(DailySongPersistenceTest.SafeDatabaseCondition.class)
@SpringBootTest
class DailySongOutboxClaimRepositoryTest {
    @Autowired private DailySongRunRepository runs;
    @Autowired private DailySongJobRepository jobs;
    @Autowired private DailySongDispatchOutboxRepository outboxes;
    @Autowired private DailySongOutboxClaimRepository claims;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void expiredLeaseCanBeReclaimedButOldTokenCannotComplete() {
        Fixture fixture = insertFixture();
        try {
            LocalDateTime now = LocalDateTime.of(2099, 1, 1, 12, 0);
            assertThat(claims.findDueEventIds(now, 20)).contains(fixture.eventId());
            Claim first = claims.claim(fixture.eventId(), now, now.plusMinutes(1)).orElseThrow();
            assertThat(claims.claim(fixture.eventId(), now, now.plusMinutes(1))).isEmpty();

            Claim second = claims.claim(fixture.eventId(), now.plusMinutes(2), now.plusMinutes(4)).orElseThrow();
            assertThat(second.eventId()).isEqualTo(fixture.eventId());
            assertThat(second.token()).isNotEqualTo(first.token());
            assertThat(second.attemptCount()).isEqualTo(2);
            assertThat(claims.markPublished(first, now.plusMinutes(2))).isZero();
            assertThat(claims.markPublished(second, now.plusMinutes(2))).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_dispatch_outbox WHERE event_id = ?
                    """, String.class, fixture.eventId())).isEqualTo("PUBLISHED");
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void concurrentInstancesCannotClaimSameEvent() throws Exception {
        Fixture fixture = insertFixture();
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            LocalDateTime now = LocalDateTime.of(2099, 1, 1, 12, 0);
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return claims.claim(fixture.eventId(), now, now.plusMinutes(2)).isPresent();
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return claims.claim(fixture.eventId(), now, now.plusMinutes(2)).isPresent();
            });
            start.countDown();

            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void failedPublishRemainsDueOnlyAfterRetryTime() {
        Fixture fixture = insertFixture();
        try {
            LocalDateTime now = LocalDateTime.of(2099, 1, 1, 12, 0);
            Claim first = claims.claim(fixture.eventId(), now, now.plusMinutes(2)).orElseThrow();
            assertThat(claims.scheduleRetry(first, now.plusSeconds(10), "NACKED")).isEqualTo(1);
            assertThat(claims.claim(fixture.eventId(), now, now.plusMinutes(2))).isEmpty();
            assertThat(claims.findDueEventIds(now, 20)).doesNotContain(fixture.eventId());

            Claim second = claims.claim(fixture.eventId(), now.plusSeconds(10), now.plusMinutes(2)).orElseThrow();
            assertThat(second.attemptCount()).isEqualTo(2);
            assertThat(claims.markPublished(second, now.plusSeconds(10))).isEqualTo(1);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void cancelledJobIsNotPublished() {
        Fixture fixture = insertFixture();
        try {
            jdbcTemplate.update("UPDATE daily_song_jobs SET status = 'CANCELLED' WHERE job_id = ?", fixture.jobId());
            LocalDateTime now = LocalDateTime.of(2099, 1, 1, 12, 0);

            assertThat(claims.findDueEventIds(now, 20)).doesNotContain(fixture.eventId());
            assertThat(claims.claim(fixture.eventId(), now, now.plusMinutes(2))).isEmpty();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_dispatch_outbox WHERE event_id = ?
                    """, String.class, fixture.eventId())).isEqualTo("PENDING");
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void firstAttemptIsDueRegardlessOfDatabaseDefaultTimeZone() {
        Fixture fixture = insertFixture();
        try {
            LocalDateTime now = LocalDateTime.of(2099, 1, 1, 12, 0);
            jdbcTemplate.update("""
                    UPDATE daily_song_dispatch_outbox SET next_publish_at = ? WHERE event_id = ?
                    """, now.plusHours(9), fixture.eventId());

            assertThat(claims.findDueEventIds(now, 20)).contains(fixture.eventId());
            assertThat(claims.claim(fixture.eventId(), now, now.plusMinutes(2))).isPresent();
        } finally {
            deleteFixture(fixture);
        }
    }

    private Fixture insertFixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User user = users.saveAndFlush(User.signup("daily-" + suffix + "@example.test", "test-hash", "ds" + suffix));
            LocalDate date = LocalDate.of(1000, 1, 1).plusDays(user.getUserId() - 1);
            runs.saveAndFlush(new DailySongRun(date, date.atTime(0, 30)));
            DailySongJob job = jobs.saveAndFlush(new DailySongJob(date, user.getUserId(), UUID.randomUUID().toString()));
            String eventId = UUID.randomUUID().toString();
            outboxes.saveAndFlush(new DailySongDispatchOutbox(eventId, job.getJobId()));
            jdbcTemplate.update("""
                    UPDATE daily_song_dispatch_outbox SET next_publish_at = ? WHERE event_id = ?
                    """, LocalDateTime.of(2000, 1, 1, 0, 0), eventId);
            return new Fixture(eventId, job.getJobId(), date, user.getUserId());
        });
    }

    private void deleteFixture(Fixture fixture) {
        jdbcTemplate.update("DELETE FROM daily_song_dispatch_outbox WHERE event_id = ?", fixture.eventId());
        jdbcTemplate.update("DELETE FROM daily_song_jobs WHERE job_id = ?", fixture.jobId());
        jdbcTemplate.update("DELETE FROM daily_song_runs WHERE recommendation_date = ?", fixture.date());
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", fixture.userId());
    }

    private record Fixture(String eventId, long jobId, LocalDate date, long userId) {
    }
}
