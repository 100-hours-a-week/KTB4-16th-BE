package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.google.cloud.storage.Storage;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.integrationtest.IntegrationTestDatabaseGuard;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class VoteMatchChatRoomMysqlIntegrationTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.now(KST);
    private static final ObjectMapper HTTP_OBJECT_MAPPER = new ObjectMapper();

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean Storage storage;
    @MockitoBean AiVoteReadRepository aiVoteReadRepository;
    @MockitoBean Clock clock;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> questionIds = new ArrayList<>();
    private boolean ownsSnapshot;

    @DynamicPropertySource
    static void registerChatServer(DynamicPropertyRegistry registry) {
        registry.add("chat-server.base-url", ChatServerStub::start);
        registry.add("chat-server.internal-api-key", () -> "integration-test-chat-key");
    }

    @BeforeEach
    void verifiesIsolatedDatabaseAndSetsClock() throws Exception {
        IntegrationTestDatabaseGuard.verify(dataSource, jdbcTemplate);
        ChatServerStub.jdbcTemplate = jdbcTemplate;
        ChatServerStub.reset();
        when(clock.instant()).thenReturn(TODAY.atTime(12, 0).atZone(KST).toInstant());
        when(clock.getZone()).thenReturn(KST);
    }

    @AfterEach
    void removesOnlyRowsCreatedByThisTest() {
        if (!questionIds.isEmpty()) {
            String questionPlaceholders = placeholders(questionIds.size());
            jdbcTemplate.update("DELETE FROM match_requests WHERE vote_answer_id IN "
                            + "(SELECT vote_answer_id FROM vote_answers WHERE vote_question_id IN ("
                            + questionPlaceholders + "))", questionIds.toArray());
            jdbcTemplate.update("DELETE FROM matches WHERE vote_question_id IN ("
                    + questionPlaceholders + ")", questionIds.toArray());
        }
        if (!userIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM vote_user_daily_assignments WHERE user_id IN ("
                    + placeholders(userIds.size()) + ")", userIds.toArray());
        }
        for (Long questionId : questionIds) {
            jdbcTemplate.update("DELETE FROM vote_answers WHERE vote_question_id = ?", questionId);
            jdbcTemplate.update("DELETE FROM vote_questions WHERE vote_question_id = ?", questionId);
        }
        if (!userIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM vote_answers WHERE user_id IN ("
                    + placeholders(userIds.size()) + ")", userIds.toArray());
            jdbcTemplate.update("DELETE FROM users WHERE user_id IN ("
                    + placeholders(userIds.size()) + ")", userIds.toArray());
        }
        if (ownsSnapshot) {
            jdbcTemplate.update("DELETE FROM vote_daily_snapshots WHERE vote_date = ?", TODAY);
            ownsSnapshot = false;
        }
        questionIds.clear();
        userIds.clear();
    }

    @AfterAll
    static void stopMockChatServer() {
        ChatServerStub.stop();
    }

    @Test
    void submitsVotesMatchesAndCreatesOneChatRoomAfterMatchCommit() throws Exception {
        ChatServerStub.respond(201, "{\"chatRoomId\":78123}");
        Fixture fixture = createFixture();

        JsonNode questionA = getToday(fixture.userA());
        JsonNode questionB = getToday(fixture.userB());
        assertThat(questionA.path("data").path("voteQuestionId").asLong())
                .isEqualTo(fixture.questionId());
        assertThat(questionB.path("data").path("voteQuestionId").asLong())
                .isEqualTo(fixture.questionId());

        long answerA = submitAnswer(fixture.userA(), fixture.questionId(), "A");
        long answerB = submitAnswer(fixture.userB(), fixture.questionId(), "B");
        assertThat(answerA).isPositive();
        assertThat(answerB).isPositive().isNotEqualTo(answerA);

        long requestA = createMatchRequest(fixture.userA(), answerA);
        assertThat(status(fixture.userA(), requestA).path("data").path("status").asText())
                .isEqualTo("WAITING");

        long requestB = createMatchRequest(fixture.userB(), answerB);

        JsonNode matchedA = status(fixture.userA(), requestA);
        JsonNode matchedB = status(fixture.userB(), requestB);
        assertThat(matchedA.path("data").path("status").asText()).isEqualTo("MATCHED");
        assertThat(matchedB.path("data").path("status").asText()).isEqualTo("MATCHED");
        assertThat(matchedA.path("data").path("chatRoomId").asLong()).isEqualTo(78123L);
        assertThat(matchedB.path("data").path("chatRoomId").asLong()).isEqualTo(78123L);

        JsonNode refreshedVoteA = getToday(fixture.userA());
        JsonNode refreshedVoteB = getToday(fixture.userB());
        assertThat(refreshedVoteA.path("data").path("myAnswer").asText()).isEqualTo("선택 A");
        assertThat(refreshedVoteA.path("data").path("result").path("optionAPercent").asInt())
                .isEqualTo(50);
        assertThat(refreshedVoteA.path("data").path("result").path("optionBPercent").asInt())
                .isEqualTo(50);
        assertThat(refreshedVoteB.path("data").path("myAnswer").asText()).isEqualTo("선택 B");
        assertThat(refreshedVoteB.path("data").path("result").path("optionAPercent").asInt())
                .isEqualTo(50);
        assertThat(refreshedVoteB.path("data").path("result").path("optionBPercent").asInt())
                .isEqualTo(50);

        assertThat(ChatServerStub.requests).hasSize(1);
        ObservedRequest observed = ChatServerStub.requests.getFirst();
        JsonNode chatRequest = objectMapper.readTree(observed.body());
        long matchId = chatRequest.path("matchId").asLong();
        assertThat(chatRequest.path("participants")).hasSize(2);
        assertThat(participantField(chatRequest.path("participants"), "userId"))
                .containsExactlyInAnyOrder(String.valueOf(fixture.userA()), String.valueOf(fixture.userB()));
        assertThat(participantField(chatRequest.path("participants"), "nickname"))
                .containsExactlyInAnyOrder(fixture.nicknameA(), fixture.nicknameB());
        assertThat(OffsetDateTime.parse(chatRequest.path("expiresAt").asText()))
                .isEqualTo(TODAY.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(9)));
        assertThat(observed.committedMatchCount()).isEqualTo(1);
        assertThat(observed.committedMatchingRequestCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT chat_room_id IS NOT NULL AND matched_at IS NOT NULL FROM matches WHERE match_id = ?",
                Boolean.class, matchId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_requests WHERE match_id = ? AND status = 'MATCHED'",
                Integer.class, matchId)).isEqualTo(2);
    }

    @Test
    void retriesServerErrorsThreeTimesThenFailsBothRequestsAndDoesNotReuseFailedCandidate() throws Exception {
        ChatServerStub.respond(503, "{\"code\":\"INTERNAL_ERROR\"}");
        ChatServerStub.respond(503, "{\"code\":\"INTERNAL_ERROR\"}");
        ChatServerStub.respond(503, "{\"code\":\"INTERNAL_ERROR\"}");
        Fixture fixture = createFixture();
        long answerA = submitAnswer(fixture.userA(), fixture.questionId(), "A");
        long answerB = submitAnswer(fixture.userB(), fixture.questionId(), "B");
        long requestA = createMatchRequest(fixture.userA(), answerA);
        long requestB = createMatchRequest(fixture.userB(), answerB);

        assertThat(status(fixture.userA(), requestA).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(status(fixture.userB(), requestB).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(status(fixture.userA(), requestA).path("data").path("chatRoomId").isNull()).isTrue();
        assertThat(ChatServerStub.requests).hasSize(3);
        long matchId = ChatServerStub.matchIdFrom(ChatServerStub.requests.getFirst().body());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE chat_room_id IS NULL AND matched_at IS NULL AND match_id = ?",
                Integer.class, matchId)).isEqualTo(1);

        long userC = createUser("retry-c");
        assignUser(userC, fixture.questionId());
        long answerC = submitAnswer(userC, fixture.questionId(), "B");
        long requestC = createMatchRequest(userC, answerC);
        assertThat(status(userC, requestC).path("data").path("status").asText()).isEqualTo("WAITING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matches WHERE vote_question_id = ?",
                Integer.class, fixture.questionId())).isEqualTo(1);
    }

    @Test
    void doesNotRetryChatRoomConflictAndFailsBothRequests() throws Exception {
        ChatServerStub.respond(409, "{\"code\":\"CHAT_ROOM_CONFLICT\"}");
        Fixture fixture = createFixture();
        long answerA = submitAnswer(fixture.userA(), fixture.questionId(), "A");
        long answerB = submitAnswer(fixture.userB(), fixture.questionId(), "B");
        long requestA = createMatchRequest(fixture.userA(), answerA);
        long requestB = createMatchRequest(fixture.userB(), answerB);

        assertThat(status(fixture.userA(), requestA).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(status(fixture.userB(), requestB).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(status(fixture.userA(), requestA).path("data").path("chatRoomId").isNull()).isTrue();
        assertThat(ChatServerStub.requests).hasSize(1);
        long matchId = ChatServerStub.matchIdFrom(ChatServerStub.requests.getFirst().body());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE chat_room_id IS NULL AND matched_at IS NULL AND match_id = ?",
                Integer.class, matchId)).isEqualTo(1);
    }

    @Test
    void retriesTransientFailureWithIdenticalBodyAndCompletesOnce() throws Exception {
        ChatServerStub.respond(503, "{\"code\":\"INTERNAL_ERROR\"}");
        ChatServerStub.respond(201, "{\"chatRoomId\":78124}");
        Fixture fixture = createFixture();
        long answerA = submitAnswer(fixture.userA(), fixture.questionId(), "A");
        long answerB = submitAnswer(fixture.userB(), fixture.questionId(), "B");
        long requestA = createMatchRequest(fixture.userA(), answerA);
        long requestB = createMatchRequest(fixture.userB(), answerB);

        assertThat(status(fixture.userA(), requestA).path("data").path("status").asText()).isEqualTo("MATCHED");
        assertThat(status(fixture.userB(), requestB).path("data").path("status").asText()).isEqualTo("MATCHED");
        assertThat(status(fixture.userA(), requestA).path("data").path("chatRoomId").asLong()).isEqualTo(78124L);
        assertThat(status(fixture.userB(), requestB).path("data").path("chatRoomId").asLong()).isEqualTo(78124L);
        assertThat(ChatServerStub.requests).hasSize(2);
        assertThat(ChatServerStub.requests.get(1).body()).isEqualTo(ChatServerStub.requests.getFirst().body());
        assertThat(ChatServerStub.matchIdFrom(ChatServerStub.requests.get(1).body()))
                .isEqualTo(ChatServerStub.matchIdFrom(ChatServerStub.requests.getFirst().body()));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matches WHERE vote_question_id = ?",
                Integer.class, fixture.questionId())).isEqualTo(1);
    }

    private Fixture createFixture() {
        ensureReadySnapshot();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long questionId = insert("""
                INSERT INTO vote_questions (question_text, option_a, option_b, music_genre, vote_date)
                VALUES (?, ?, ?, ?, ?)
                """, "통합 질문", "선택 A", "선택 B", "E2E_" + suffix, TODAY);
        questionIds.add(questionId);
        long userA = createUser(suffix + "a");
        long userB = createUser(suffix + "b");
        assignUser(userA, questionId);
        assignUser(userB, questionId);
        String nicknameA = nickname(userA);
        String nicknameB = nickname(userB);
        return new Fixture(questionId, userA, userB, nicknameA, nicknameB);
    }

    private void ensureReadySnapshot() {
        List<String> states = jdbcTemplate.query("SELECT status FROM vote_daily_snapshots WHERE vote_date = ?",
                (resultSet, rowNumber) -> resultSet.getString("status"), TODAY);
        if (states.isEmpty()) {
            jdbcTemplate.update("INSERT INTO vote_daily_snapshots (vote_date, status) VALUES (?, 'READY')", TODAY);
            ownsSnapshot = true;
        } else {
            assertThat(states).containsExactly("READY");
        }
    }

    private long createUser(String suffix) {
        long userId = insert("""
                INSERT INTO users (email, password_hash, nickname, preferred_genres, genre_onboarding_done)
                VALUES (?, ?, ?, JSON_ARRAY('재즈'), TRUE)
                """, "chat-e2e-" + suffix + "@example.test", "integration-test-hash",
                "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        userIds.add(userId);
        return userId;
    }

    private String nickname(long userId) {
        return jdbcTemplate.queryForObject("SELECT nickname FROM users WHERE user_id = ?", String.class, userId);
    }

    private void assignUser(long userId, long questionId) {
        String genre = jdbcTemplate.queryForObject(
                "SELECT music_genre FROM vote_questions WHERE vote_question_id = ?", String.class, questionId);
        jdbcTemplate.update("""
                INSERT INTO vote_user_daily_assignments (vote_date, user_id, music_genre, vote_question_id)
                VALUES (?, ?, ?, ?)
                """, TODAY, userId, genre, questionId);
    }

    private JsonNode getToday(long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/vote-questions/today")
                        .header("Authorization", bearer(userId)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long submitAnswer(long userId, long questionId, String option) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vote-questions/{id}/answers", questionId)
                        .header("Authorization", bearer(userId))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"selectedOption\":\"" + option + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("voteAnswerId").asLong();
    }

    private long createMatchRequest(long userId, long voteAnswerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/match-requests")
                        .header("Authorization", bearer(userId))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"voteAnswerId\":" + voteAnswerId + "}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("matchRequestId").asLong();
    }

    private JsonNode status(long userId, long matchRequestId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/match-requests/{id}", matchRequestId)
                        .header("Authorization", bearer(userId)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private List<String> participantField(JsonNode participants, String field) {
        List<String> values = new ArrayList<>();
        participants.forEach(participant -> values.add(participant.path(field).asText()));
        return values;
    }

    private record Fixture(long questionId, long userA, long userB, String nicknameA, String nicknameB) { }

    private record StubResponse(int status, String body) { }

    private record ObservedRequest(String body, int committedMatchCount, int committedMatchingRequestCount) { }

    private static final class ChatServerStub {
        private static final LinkedBlockingQueue<StubResponse> responses = new LinkedBlockingQueue<>();
        private static final List<ObservedRequest> requests = new CopyOnWriteArrayList<>();
        private static HttpServer server;
        private static JdbcTemplate jdbcTemplate;

        private static synchronized String start() {
            if (server == null) {
                try {
                    server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
                    server.createContext("/internal/chat-rooms", ChatServerStub::handle);
                    server.start();
                } catch (IOException exception) {
                    throw new IllegalStateException("Mock Chat Server를 시작하지 못했습니다.", exception);
                }
            }
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private static void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            long matchId = matchIdFrom(body);
            int committedMatches = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM matches WHERE match_id = ?", Integer.class, matchId);
            int committedRequests = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM match_requests WHERE match_id = ? AND status = 'MATCHING'",
                    Integer.class, matchId);
            requests.add(new ObservedRequest(body, committedMatches, committedRequests));
            StubResponse response = responses.poll();
            if (response == null) {
                response = new StubResponse(500, "{\"code\":\"INTERNAL_ERROR\"}");
            }
            byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        }

        private static void respond(int status, String body) {
            responses.add(new StubResponse(status, body));
        }

        private static long matchIdFrom(String body) {
            try {
                return HTTP_OBJECT_MAPPER.readTree(body).path("matchId").asLong();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Mock Chat Server 요청에 matchId가 없습니다.", exception);
            }
        }

        private static void reset() {
            responses.clear();
            requests.clear();
        }

        private static synchronized void stop() {
            if (server != null) {
                server.stop(0);
                server = null;
            }
        }
    }
}
