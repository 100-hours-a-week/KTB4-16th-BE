package com.ktb4.team16.mulo.vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.scheduler.VoteDailySnapshotService;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailySnapshotWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteUserGenreAssignment;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hamcrest.Matchers;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(VoteQuestionSnapshotMysqlIntegrationTest.FixedClockConfiguration.class)
class VoteQuestionSnapshotMysqlIntegrationTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired VoteDailySnapshotService snapshotService;
    @Autowired VoteDailySnapshotWriter snapshotWriter;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired MockMvc mockMvc;

    @MockitoBean Storage storage;
    @MockitoBean AiVoteReadRepository aiVoteReadRepository;

    private final Set<LocalDate> testDates = new java.util.LinkedHashSet<>();
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void verifiesIsolatedDatabaseAndAppliedMigrations() throws Exception {
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
    }

    @AfterEach
    void removesOnlyRowsCreatedByThisTest() {
        for (LocalDate date : testDates) {
            jdbcTemplate.update("DELETE FROM vote_user_daily_assignments WHERE vote_date = ?", date);
            jdbcTemplate.update("DELETE FROM vote_answers WHERE vote_question_id IN "
                    + "(SELECT vote_question_id FROM vote_questions WHERE vote_date = ?)", date);
            jdbcTemplate.update("DELETE FROM vote_daily_snapshots WHERE vote_date = ?", date);
            jdbcTemplate.update("DELETE FROM vote_questions WHERE vote_date = ?", date);
        }
        if (!userIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
            jdbcTemplate.update("DELETE FROM users WHERE user_id IN (" + placeholders + ")", userIds.toArray());
        }
    }

    @Test
    void existingMysqlQuestionWinsAndReadySnapshotIsImmutable() {
        long userId = insertUser("재즈");
        long questionId = insertQuestion(TODAY, "재즈", "BE 고정 질문", "저장 A", "저장 B");
        long answerId = insertAnswer(questionId, userId, "저장 A");
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(userId, "재즈"));

        createSnapshot(TODAY);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT question_text FROM vote_questions WHERE vote_question_id = ?", String.class, questionId))
                .isEqualTo("BE 고정 질문");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT vote_question_id FROM vote_user_daily_assignments WHERE vote_date = ? AND user_id = ?",
                Long.class, TODAY, userId)).isEqualTo(questionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT vote_answer_id FROM vote_answers WHERE vote_question_id = ? AND user_id = ?",
                Long.class, questionId, userId)).isEqualTo(answerId);
        verify(aiVoteReadRepository, never()).findQuestions(TODAY.minusDays(1), TODAY);

        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(userId, "공통"));
        createSnapshot(TODAY);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT music_genre FROM vote_user_daily_assignments WHERE vote_date = ? AND user_id = ?",
                String.class, TODAY, userId)).isEqualTo("재즈");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_daily_snapshots WHERE vote_date = ? AND status = 'READY'",
                Integer.class, TODAY)).isEqualTo(1);
    }

    @Test
    void storesAiTodayQuestionAndSharesItAcrossSameGroupUsers() {
        long firstUserId = insertUser("공통");
        long secondUserId = insertUser("공통");
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres())
                .thenReturn(Map.of(firstUserId, "공통", secondUserId, "공통"));
        when(aiVoteReadRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of(
                new AiVoteQuestion(TODAY, "공통", "AI 질문", "AI A", "AI B")));

        createSnapshot(TODAY);

        Long questionId = jdbcTemplate.queryForObject(
                "SELECT vote_question_id FROM vote_questions WHERE vote_date = ? AND music_genre = '공통'",
                Long.class, TODAY);
        assertThat(jdbcTemplate.queryForList(
                "SELECT vote_question_id FROM vote_user_daily_assignments "
                        + "WHERE vote_date = ? AND user_id IN (?, ?)", Long.class,
                TODAY, firstUserId, secondUserId)).containsExactly(questionId, questionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT question_text FROM vote_questions WHERE vote_question_id = ?", String.class, questionId))
                .isEqualTo("AI 질문");
    }

    @Test
    void copiesPreviousDayQuestionIntoNewTodayQuestionWithoutCopyingAnswers() {
        LocalDate yesterday = TODAY.minusDays(1);
        testDates.add(yesterday);
        long userId = insertUser("재즈");
        long yesterdayQuestionId = insertQuestion(yesterday, "재즈", "어제 질문", "어제 A", "어제 B");
        insertAnswer(yesterdayQuestionId, userId, "어제 A");
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(userId, "재즈"));
        when(aiVoteReadRepository.findQuestions(yesterday, TODAY)).thenReturn(List.of(
                new AiVoteQuestion(yesterday, "재즈", "어제 질문", "어제 A", "어제 B")));

        createSnapshot(TODAY);

        Long todayQuestionId = jdbcTemplate.queryForObject(
                "SELECT vote_question_id FROM vote_questions WHERE vote_date = ? AND music_genre = '재즈'",
                Long.class, TODAY);
        assertThat(todayQuestionId).isNotEqualTo(yesterdayQuestionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT question_text FROM vote_questions WHERE vote_question_id = ?", String.class, todayQuestionId))
                .isEqualTo("어제 질문");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_answers WHERE vote_question_id = ?", Integer.class, todayQuestionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT vote_question_id FROM vote_user_daily_assignments WHERE vote_date = ? AND user_id = ?",
                Long.class, TODAY, userId)).isEqualTo(todayQuestionId);
    }

    @Test
    void apiReturnsExistingAnswerAndRejectsUnauthenticatedRequest() throws Exception {
        long userId = insertUser("재즈");
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(userId, "재즈"));
        when(aiVoteReadRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of(
                new AiVoteQuestion(TODAY, "재즈", "질문 내용", "선택 A", "선택 B")));
        createSnapshot(TODAY);
        Long questionId = jdbcTemplate.queryForObject(
                "SELECT vote_question_id FROM vote_questions WHERE vote_date = ? AND music_genre = '재즈'",
                Long.class, TODAY);
        insertAnswer(questionId, userId, "선택 B");
        when(aiVoteReadRepository.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/vote-questions/today"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/vote-questions/today")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voteQuestionId").value(questionId))
                .andExpect(jsonPath("$.data.question").value("질문 내용"))
                .andExpect(jsonPath("$.data.optionA").value("선택 A"))
                .andExpect(jsonPath("$.data.optionB").value("선택 B"))
                .andExpect(jsonPath("$.data.myAnswer").value("선택 B"))
                .andExpect(jsonPath("$.data.voteAnswerId").isNumber());
        verify(aiVoteReadRepository, never()).findUserGenre(userId);
    }

    @Test
    void apiReturnsNullAnswerFieldsForUnvotedUser() throws Exception {
        long userId = insertUser("재즈");
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(userId, "재즈"));
        when(aiVoteReadRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of(
                new AiVoteQuestion(TODAY, "재즈", "질문 내용", "선택 A", "선택 B")));
        createSnapshot(TODAY);

        mockMvc.perform(get("/api/vote-questions/today")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myAnswer").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.voteAnswerId").value(Matchers.nullValue()));
    }

    @Test
    void userWithoutPreferenceOrRecordGetsCommonQuestionWhenAiUserRowIsMissing() throws Exception {
        long userId = insertUserWithoutPreference();
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of());
        when(aiVoteReadRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of(
                new AiVoteQuestion(TODAY, "공통", "공통 질문", "선택 A", "선택 B")));
        createSnapshot(TODAY);

        Long questionId = jdbcTemplate.queryForObject(
                "SELECT vote_question_id FROM vote_questions WHERE vote_date = ? AND music_genre = '공통'",
                Long.class, TODAY);

        mockMvc.perform(get("/api/vote-questions/today")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voteQuestionId").value(questionId))
                .andExpect(jsonPath("$.data.question").value("공통 질문"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT music_genre FROM vote_user_daily_assignments WHERE vote_date = ? AND user_id = ?",
                String.class, TODAY, userId)).isEqualTo("공통");
    }

    @Test
    void assignmentToQuestionFromAnotherDateIsReportedAsSnapshotNotReady() throws Exception {
        long userId = insertUser("재즈");
        LocalDate yesterday = TODAY.minusDays(1);
        testDates.add(TODAY);
        testDates.add(yesterday);
        long previousQuestionId = insertQuestion(yesterday, "재즈", "이전 질문", "A", "B");
        jdbcTemplate.update("INSERT INTO vote_daily_snapshots (vote_date, status) VALUES (?, 'READY')", TODAY);
        jdbcTemplate.update("INSERT INTO vote_user_daily_assignments "
                        + "(vote_date, user_id, music_genre, vote_question_id) VALUES (?, ?, '재즈', ?)",
                TODAY, userId, previousQuestionId);

        mockMvc.perform(get("/api/vote-questions/today")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VOTE_SNAPSHOT_NOT_READY"));
    }

    @Test
    void apiReturnsNotFoundWhenTodaysAndPreviousGroupQuestionsAreMissing() throws Exception {
        long userId = insertUser("재즈");
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenReturn(Map.of(userId, "재즈"));
        when(aiVoteReadRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of());
        createSnapshot(TODAY);

        mockMvc.perform(get("/api/vote-questions/today")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODAY_VOTE_NOT_FOUND"));
    }

    @Test
    void failedSnapshotWriteRollsBackQuestionsAndReadyState() {
        LocalDate date = TODAY.minusDays(2);
        testDates.add(date);
        long userId = insertUser("재즈");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshotWriter.write(date,
                        List.of(new AiVoteQuestion(date, "재즈", "질문", "A", "B")),
                        List.of(new VoteUserGenreAssignment(userId + 999_999L, "재즈"))))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_daily_snapshots WHERE vote_date = ?", Integer.class, date)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_questions WHERE vote_date = ?", Integer.class, date)).isZero();
    }

    @Test
    void simultaneousSnapshotExecutionsLeaveOneReadySnapshotAndOneAssignment() throws Exception {
        long userId = insertUser("재즈");
        CountDownLatch bothReadGenres = new CountDownLatch(2);
        when(aiVoteReadRepository.isAvailable()).thenReturn(true);
        when(aiVoteReadRepository.findAllUserGenres()).thenAnswer(invocation -> {
            bothReadGenres.countDown();
            if (!bothReadGenres.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent snapshot calls did not synchronize.");
            }
            return Map.of(userId, "재즈");
        });
        when(aiVoteReadRepository.findQuestions(TODAY.minusDays(1), TODAY)).thenReturn(List.of(
                new AiVoteQuestion(TODAY, "재즈", "질문", "A", "B")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> createSnapshot(TODAY));
            var second = executor.submit(() -> createSnapshot(TODAY));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_daily_snapshots WHERE vote_date = ? AND status = 'READY'",
                Integer.class, TODAY)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_user_daily_assignments WHERE vote_date = ? AND user_id = ?",
                Integer.class, TODAY, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vote_questions WHERE vote_date = ? AND music_genre = '재즈'",
                Integer.class, TODAY)).isEqualTo(1);
    }

    private long insertUser(String genre) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long userId = insert("""
                INSERT INTO users (email, password_hash, nickname, preferred_genres, genre_onboarding_done)
                VALUES (?, ?, ?, JSON_ARRAY(?), TRUE)
                """, suffix + "@example.test", "test-password-hash", "u" + suffix, genre);
        userIds.add(userId);
        return userId;
    }

    private long insertUserWithoutPreference() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long userId = insert("""
                INSERT INTO users (email, password_hash, nickname, preferred_genres, genre_onboarding_done)
                VALUES (?, ?, ?, NULL, FALSE)
                """, suffix + "@example.test", "test-password-hash", "u" + suffix);
        userIds.add(userId);
        return userId;
    }

    private long insertQuestion(LocalDate date, String genre, String question, String optionA, String optionB) {
        testDates.add(date);
        return insert("""
                INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date)
                VALUES (?, ?, ?, ?, ?)
                """, question, optionA, optionB, genre, date);
    }

    private long insertAnswer(long questionId, long userId, String selectedOption) {
        return insert("""
                INSERT INTO vote_answers (vote_question_id, user_id, selected_option)
                VALUES (?, ?, ?)
                """, questionId, userId, selectedOption);
    }

    private void createSnapshot(LocalDate date) {
        testDates.add(date);
        snapshotService.createSnapshot(date);
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
            return Clock.fixed(TODAY.atTime(12, 0).atZone(KST).toInstant(), KST);
        }
    }
}
