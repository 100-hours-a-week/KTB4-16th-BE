package com.ktb4.team16.mulo.vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(VoteAnswerSubmitMysqlIntegrationTest.FixedClockConfiguration.class)
class VoteAnswerSubmitMysqlIntegrationTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired MockMvc mockMvc;

    @MockitoBean Storage storage;
    @MockitoBean AiVoteReadRepository aiVoteReadRepository;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> questionIds = new ArrayList<>();
    private boolean snapshotCreated;

    @BeforeEach
    void verifiesIsolatedDatabaseAndAppliedMigrations() throws Exception {
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
    }

    @AfterEach
    void removesOnlyRowsCreatedByThisTest() {
        if (!userIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
            jdbcTemplate.update("DELETE FROM vote_user_daily_assignments WHERE user_id IN (" + placeholders + ")",
                    userIds.toArray());
            jdbcTemplate.update("DELETE FROM vote_answers WHERE user_id IN (" + placeholders + ")",
                    userIds.toArray());
        }
        for (Long questionId : questionIds) {
            jdbcTemplate.update("DELETE FROM vote_answers WHERE vote_question_id = ?", questionId);
            jdbcTemplate.update("DELETE FROM vote_questions WHERE vote_question_id = ?", questionId);
        }
        if (snapshotCreated) {
            jdbcTemplate.update("DELETE FROM vote_daily_snapshots WHERE vote_date = ?", TODAY);
            snapshotCreated = false;
        }
        if (!userIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
            jdbcTemplate.update("DELETE FROM users WHERE user_id IN (" + placeholders + ")", userIds.toArray());
        }
    }

    @Test
    void persistsAnswerAndReturnsCurrentMysqlAggregateAfter2300() throws Exception {
        long userId = prepareAssignedUser("재즈");

        MvcResult result = submit(userId, questionIds.getFirst(), "A");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"voteAnswerId\"", "\"myAnswer\":\"선택 A\"",
                        "\"optionAPercent\":100", "\"optionBPercent\":0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_answers WHERE user_id = ? AND vote_question_id = ?",
                Integer.class, userId, questionIds.getFirst())).isEqualTo(1);
    }

    @Test
    void simultaneousDuplicateSubmissionsCreateOnlyOneAnswerAndReturnConflictForOther() throws Exception {
        long userId = prepareAssignedUser("인디음악");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> submitAfterGate(userId, ready, start));
            var second = executor.submit(() -> submitAfterGate(userId, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int firstStatus = first.get(20, TimeUnit.SECONDS).getResponse().getStatus();
            int secondStatus = second.get(20, TimeUnit.SECONDS).getResponse().getStatus();

            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_answers WHERE user_id = ? AND vote_question_id = ?",
                Integer.class, userId, questionIds.getFirst())).isEqualTo(1);
    }

    @Test
    void postWithoutPriorGetCreatesTheUsersDailyAssignmentAndSubmits() throws Exception {
        long userId = insertUser();
        userIds.add(userId);
        long questionId = insertQuestion("공통");
        questionIds.add(questionId);
        jdbcTemplate.update("INSERT INTO vote_daily_snapshots (vote_date, status) VALUES (?, 'READY')", TODAY);
        snapshotCreated = true;
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findUserGenre(userId)).thenReturn(java.util.Optional.empty());

        MvcResult result = submit(userId, questionId, "B");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT music_genre FROM vote_user_daily_assignments WHERE vote_date = ? AND user_id = ?",
                String.class, TODAY, userId)).isEqualTo("공통");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_answers WHERE user_id = ? AND vote_question_id = ?",
                Integer.class, userId, questionId)).isEqualTo(1);
    }

    private long prepareAssignedUser(String genre) {
        long userId = insertUser();
        userIds.add(userId);
        long questionId = insertQuestion(genre);
        questionIds.add(questionId);
        jdbcTemplate.update("INSERT INTO vote_daily_snapshots (vote_date, status) VALUES (?, 'READY')", TODAY);
        snapshotCreated = true;
        jdbcTemplate.update("INSERT INTO vote_user_daily_assignments "
                        + "(vote_date, user_id, music_genre, vote_question_id) VALUES (?, ?, ?, ?)",
                TODAY, userId, genre, questionId);
        return userId;
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return insert("INSERT INTO users "
                        + "(email, password_hash, nickname, preferred_genres, genre_onboarding_done) "
                        + "VALUES (?, ?, ?, JSON_ARRAY('재즈'), TRUE)",
                suffix + "@example.test", "integration-test-hash", "u" + suffix);
    }

    private long insertQuestion(String genre) {
        return insert("INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date) "
                        + "VALUES (?, ?, ?, ?, ?)", "통합 질문", "선택 A", "선택 B", genre, TODAY);
    }

    private MvcResult submit(long userId, long questionId, String option) throws Exception {
        return mockMvc.perform(post("/api/vote-questions/" + questionId + "/answers")
                        .header("Authorization", bearer(userId))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"selectedOption\":\"" + option + "\"}"))
                .andReturn();
    }

    private MvcResult submitAfterGate(long userId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent vote submissions did not start.");
        }
        return submit(userId, questionIds.getFirst(), "B");
    }

    private String bearer(long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private long insert(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock voteIntegrationTestClock() {
            return Clock.fixed(TODAY.atTime(23, 30).atZone(KST).toInstant(), KST);
        }
    }
}
