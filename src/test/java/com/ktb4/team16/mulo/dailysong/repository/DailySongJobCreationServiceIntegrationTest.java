package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobCreationRepository.Claim;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobCreationService;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
class DailySongJobCreationServiceIntegrationTest {
    private static final LocalDate MYSQL_DATE_MIN = LocalDate.of(1000, 1, 1);
    private static final long MAX_TEST_USER_ID = ChronoUnit.DAYS.between(
            MYSQL_DATE_MIN, LocalDate.of(9999, 12, 31)) + 1;

    @Autowired private DailySongJobCreationService service;
    @Autowired private DailySongJobCreationRepository creations;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @Test
    void resumesPartiallyCreatedRunAndKeepsFixedCutoff() {
        List<Long> ids = insertUsers();
        LocalDate date = null;
        boolean createdRun = false;
        try {
            assertThat(ids.getFirst()).isBetween(1L, MAX_TEST_USER_ID);
            date = MYSQL_DATE_MIN.plusDays(ids.getFirst() - 1);
            LocalDateTime cutoff = date.atTime(0, 30);
            jdbcTemplate.update("UPDATE users SET created_at = ? WHERE user_id IN (?, ?)",
                    cutoff.minusSeconds(1), ids.get(0), ids.get(1));
            jdbcTemplate.update("UPDATE users SET created_at = ? WHERE user_id = ?", cutoff.plusSeconds(1), ids.get(2));
            Long otherTargets = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM users
                    WHERE created_at <= ? AND (deleted_at IS NULL OR deleted_at > ?)
                        AND user_id NOT IN (?, ?, ?)
                    """, Long.class, cutoff, cutoff, ids.get(0), ids.get(1), ids.get(2));
            assumeTrue(otherTargets != null && otherTargets == 0);
            Long existingRuns = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM daily_song_runs WHERE recommendation_date = ?
                    """, Long.class, date);
            assumeTrue(existingRuns != null && existingRuns == 0);

            Claim previous = creations.claimRun(date, cutoff, Duration.ofMinutes(2)).orElseThrow();
            createdRun = true;
            creations.writeBatch(previous, List.of(ids.getFirst()), Duration.ofMinutes(2));
            jdbcTemplate.update("UPDATE daily_song_runs SET lease_until = ? WHERE recommendation_date = ?",
                    LocalDateTime.now(clock).minusSeconds(1), date);

            service.createJobs(date);
            service.createJobs(date);

            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_runs WHERE recommendation_date = ?
                    """, String.class, date)).isEqualTo("READY");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT target_count FROM daily_song_runs WHERE recommendation_date = ?
                    """, Long.class, date)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM daily_song_jobs WHERE recommendation_date = ?
                    """, Long.class, date)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM daily_song_dispatch_outbox o
                    JOIN daily_song_jobs j ON j.job_id = o.job_id WHERE j.recommendation_date = ?
                    """, Long.class, date)).isEqualTo(2);
        } finally {
            deleteFixture(date, ids, createdRun);
        }
    }

    private List<Long> insertUsers() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                String suffix = UUID.randomUUID().toString().substring(0, 8);
                User user = users.saveAndFlush(User.signup(
                        "daily-" + suffix + "@example.test", "test-hash", "ds" + suffix));
                ids.add(user.getUserId());
            }
            return ids;
        });
    }

    private void deleteFixture(LocalDate date, List<Long> ids, boolean createdRun) {
        if (date != null && createdRun) {
            jdbcTemplate.update("""
                    DELETE o FROM daily_song_dispatch_outbox o
                    JOIN daily_song_jobs j ON j.job_id = o.job_id
                    WHERE j.recommendation_date = ? AND j.user_id IN (?, ?, ?)
                    """, date, ids.get(0), ids.get(1), ids.get(2));
            jdbcTemplate.update("DELETE FROM daily_song_jobs WHERE recommendation_date = ? AND user_id IN (?, ?, ?)",
                    date, ids.get(0), ids.get(1), ids.get(2));
            jdbcTemplate.update("""
                    DELETE FROM daily_song_runs WHERE recommendation_date = ?
                    AND NOT EXISTS (SELECT 1 FROM daily_song_jobs WHERE recommendation_date = ?)
                    """, date, date);
        }
        for (Long id : ids) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", id);
        }
    }
}
