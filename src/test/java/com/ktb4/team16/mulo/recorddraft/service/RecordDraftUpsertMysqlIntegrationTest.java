package com.ktb4.team16.mulo.recorddraft.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.recorddraft.dto.request.RecordDraftUpsertRequest;
import com.ktb4.team16.mulo.recorddraft.repository.RecordDraftRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RecordDraftUpsertMysqlIntegrationTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordDraftService recordDraftService;
    @Autowired private RecordDraftRepository recordDraftRepository;
    @Autowired private UserRepository userRepository;
    @MockitoBean private Clock clock;
    @MockitoBean private Storage storage;

    private User user;

    @BeforeEach
    void prepareIsolatedMySqlAndUser() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseUrl = System.getenv("DB_URL");
            assertThat(connection.getMetaData().getURL()).isEqualTo(databaseUrl);
            assertThat(connection.getCatalog()).isEqualTo(
                    databaseName(databaseUrl));
        }
        assertThat(AopUtils.isAopProxy(recordDraftService)).isTrue();
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(KST);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user = userRepository.saveAndFlush(User.signup(
                suffix + "@example.test", "test-password-hash", "u" + suffix.substring(0, 8)));
    }

    private String databaseName(String jdbcUrl) {
        String urlWithoutQuery = jdbcUrl.split("\\?", 2)[0];
        return urlWithoutQuery.substring(urlWithoutQuery.lastIndexOf('/') + 1);
    }

    @AfterEach
    void removeOnlyRowsCreatedByThisTest() {
        if (user != null) {
            jdbcTemplate.update("DELETE FROM record_drafts WHERE user_id = ?", user.getUserId());
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", user.getUserId());
        }
    }

    @Test
    void serializesConcurrentFirstUpsertsForSameUser() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        RecordDraftUpsertRequest first = new RecordDraftUpsertRequest(
                null, null, 1, "first", null);
        RecordDraftUpsertRequest second = new RecordDraftUpsertRequest(
                null, null, 2, "second", null);

        try {
            List<Future<?>> requests = List.of(
                    executor.submit(() -> saveAfterBarrier(ready, start, first)),
                    executor.submit(() -> saveAfterBarrier(ready, start, second)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            requests.get(0).get(15, TimeUnit.SECONDS);
            requests.get(1).get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(recordDraftRepository.findByUser_UserId(user.getUserId()))
                .isPresent()
                .get()
                .extracting(draft -> draft.getMoodScore())
                .isIn((byte) 1, (byte) 2);
        assertThat(recordDraftRepository.countByUser_UserId(user.getUserId())).isEqualTo(1L);
    }

    private void saveAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            RecordDraftUpsertRequest request
    ) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent request barrier timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        recordDraftService.upsertRecordDraft(user.getUserId(), request);
    }
}
