package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobCreationRepository.Claim;
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
@SpringBootTest(properties = "mulo.rabbitmq.enabled=false")
class DailySongJobCreationRepositoryTest {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final LocalDate MYSQL_DATE_MIN = LocalDate.of(1000, 1, 1);
    private static final long MAX_TEST_USER_ID = ChronoUnit.DAYS.between(
            MYSQL_DATE_MIN, LocalDate.of(9999, 12, 31)) + 1;

    @Autowired private DailySongJobCreationRepository creations;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @Test
    void createsJobsAndOutboxesOnceAcrossBatches() {
        Fixture fixture = insertFixture(3);
        try {
            long withdrawnUserId = fixture.userIds().get(1);
            jdbcTemplate.update("UPDATE users SET deleted_at = ? WHERE user_id = ?",
                    fixture.cutoff().plusSeconds(1), withdrawnUserId);
            Claim claim = creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).orElseThrow();

            creations.writeBatch(claim, fixture.userIds().subList(0, 2), LEASE);
            creations.writeBatch(claim, fixture.userIds().subList(0, 2), LEASE);
            creations.writeBatch(claim, fixture.userIds().subList(2, 3), LEASE);

            assertThat(jobCount(fixture.date())).isEqualTo(3);
            assertThat(outboxCount(fixture.date())).isEqualTo(3);
            assertThat(statusForUser(fixture.date(), withdrawnUserId)).isEqualTo("CANCELLED");
            assertThat(outboxStatusForUser(fixture.date(), withdrawnUserId)).isEqualTo("CANCELLED");
            assertThat(creations.markReady(claim, 3)).isTrue();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM daily_song_runs WHERE recommendation_date = ?
                    """, String.class, fixture.date())).isEqualTo("READY");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT target_count FROM daily_song_runs WHERE recommendation_date = ?
                    """, Long.class, fixture.date())).isEqualTo(3L);
            assertThat(creations.claimRun(fixture.date(), fixture.cutoff(), LEASE)).isEmpty();
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void expiredLeaseCanBeReclaimedWithoutOldOwnerWriting() {
        Fixture fixture = insertFixture(2);
        try {
            Claim first = creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).orElseThrow();
            assertThat(creations.claimRun(fixture.date(), fixture.cutoff(), LEASE)).isEmpty();
            creations.writeBatch(first, fixture.userIds().subList(0, 1), LEASE);

            jdbcTemplate.update("UPDATE daily_song_runs SET lease_until = ? WHERE recommendation_date = ?",
                    LocalDateTime.now(clock).minusSeconds(1), fixture.date());
            Claim second = creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).orElseThrow();
            assertThat(second.token()).isNotEqualTo(first.token());
            assertThatThrownBy(() -> creations.writeBatch(first, fixture.userIds(), LEASE))
                    .hasMessageContaining("실행 권한이 만료되었거나 다른 인스턴스로 이전됐습니다.");

            creations.writeBatch(second, fixture.userIds(), LEASE);
            assertThat(creations.markReady(first, 2)).isFalse();
            assertThat(creations.markReady(second, 2)).isTrue();
            assertThat(jobCount(fixture.date())).isEqualTo(2);
            assertThat(outboxCount(fixture.date())).isEqualTo(2);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void failedBatchRollsBackAndCanBeRetried() {
        Fixture fixture = insertFixture(2);
        try {
            Claim claim = creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).orElseThrow();
            assertThatThrownBy(() -> creations.writeBatch(claim,
                    List.of(fixture.userIds().getFirst(), Long.MAX_VALUE), LEASE))
                    .hasMessageContaining("오늘의 노래 대상 사용자를 찾지 못했습니다.");
            assertThat(jobCount(fixture.date())).isZero();
            assertThat(outboxCount(fixture.date())).isZero();

            creations.writeBatch(claim, fixture.userIds(), LEASE);
            assertThatThrownBy(() -> creations.markReady(claim, 1))
                    .hasMessageContaining("발행 기록의 수가 대상 사용자 수와 다릅니다.");
            assertThat(creations.markReady(claim, 2)).isTrue();
            assertThat(jobCount(fixture.date())).isEqualTo(2);
            assertThat(outboxCount(fixture.date())).isEqualTo(2);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void zeroTargetsCanFinalizeRun() {
        Fixture fixture = insertFixture(0);
        try {
            Claim claim = creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).orElseThrow();
            assertThat(creations.markReady(claim, 0)).isTrue();
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void concurrentInstancesCannotClaimSameRun() throws Exception {
        Fixture fixture = insertFixture(0);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).isPresent();
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).isPresent();
            });
            start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void missingOutboxIsRestoredWithoutDuplicatingJob() {
        Fixture fixture = insertFixture(1);
        try {
            Claim claim = creations.claimRun(fixture.date(), fixture.cutoff(), LEASE).orElseThrow();
            creations.writeBatch(claim, fixture.userIds(), LEASE);
            jdbcTemplate.update("""
                    DELETE FROM daily_song_dispatch_outbox WHERE job_id IN
                        (SELECT job_id FROM daily_song_jobs WHERE recommendation_date = ?)
                    """, fixture.date());

            creations.writeBatch(claim, fixture.userIds(), LEASE);
            assertThat(jobCount(fixture.date())).isEqualTo(1);
            assertThat(outboxCount(fixture.date())).isEqualTo(1);
            assertThat(creations.markReady(claim, 1)).isTrue();
        } finally {
            deleteFixture(fixture);
        }
    }

    private Fixture insertFixture(int userCount) {
        List<Long> ids = new TransactionTemplate(transactionManager).execute(status -> {
            List<Long> created = new ArrayList<>();
            for (int index = 0; index < Math.max(1, userCount); index++) {
                String suffix = UUID.randomUUID().toString().substring(0, 8);
                User user = users.saveAndFlush(User.signup(
                        "daily-" + suffix + "@example.test", "test-hash", "ds" + suffix));
                created.add(user.getUserId());
            }
            return created;
        });
        assertThat(ids.getFirst()).isBetween(1L, MAX_TEST_USER_ID);
        LocalDate date = MYSQL_DATE_MIN.plusDays(ids.getFirst() - 1);
        LocalDateTime cutoff = date.atTime(0, 30);
        for (Long id : ids) {
            LocalDateTime createdAt = userCount == 0 ? cutoff.plusSeconds(1) : cutoff.minusSeconds(1);
            jdbcTemplate.update("UPDATE users SET created_at = ? WHERE user_id = ?", createdAt, id);
        }
        return new Fixture(date, cutoff, userCount == 0 ? List.of() : ids, ids);
    }

    private void deleteFixture(Fixture fixture) {
        jdbcTemplate.update("""
                DELETE o FROM daily_song_dispatch_outbox o
                JOIN daily_song_jobs j ON j.job_id = o.job_id
                WHERE j.recommendation_date = ?
                """, fixture.date());
        jdbcTemplate.update("DELETE FROM daily_song_jobs WHERE recommendation_date = ?", fixture.date());
        jdbcTemplate.update("DELETE FROM daily_song_runs WHERE recommendation_date = ?", fixture.date());
        for (Long id : fixture.createdUserIds()) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", id);
        }
    }

    private long jobCount(LocalDate date) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM daily_song_jobs WHERE recommendation_date = ?
                """, Long.class, date);
    }

    private long outboxCount(LocalDate date) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM daily_song_dispatch_outbox o
                JOIN daily_song_jobs j ON j.job_id = o.job_id
                WHERE j.recommendation_date = ?
                """, Long.class, date);
    }

    private String statusForUser(LocalDate date, long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM daily_song_jobs WHERE recommendation_date = ? AND user_id = ?
                """, String.class, date, userId);
    }

    private String outboxStatusForUser(LocalDate date, long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT o.status FROM daily_song_dispatch_outbox o
                JOIN daily_song_jobs j ON j.job_id = o.job_id
                WHERE j.recommendation_date = ? AND j.user_id = ?
                """, String.class, date, userId);
    }

    private record Fixture(LocalDate date, LocalDateTime cutoff, List<Long> userIds, List<Long> createdUserIds) {
    }
}
