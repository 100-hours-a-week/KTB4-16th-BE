package com.ktb4.team16.mulo.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.friend.service.FriendCommandTransactionalService;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.global.security.session.SessionRevocationStore;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxProcessor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
class NotificationSseHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private FriendCommandTransactionalService friendCommands;

    @Autowired
    private NotificationOutboxProcessor outboxProcessor;

    @Autowired
    private NotificationSseConnectionManager connections;

    @MockitoBean
    private SessionRevocationStore sessionRevocationStore;

    private final List<Long> createdUsers = new ArrayList<>();
    private UUID eventId;

    /** SSE HTTP 통합 테스트에서 Redis 세션을 활성 상태로 둔다. */
    @BeforeEach
    void setUp() {
        when(sessionRevocationStore.isSessionRevoked(anyString())).thenReturn(false);
        when(sessionRevocationStore.areLegacyUserTokensRevoked(anyLong())).thenReturn(false);
    }

    @Test
    void authenticatedStreamReceivesCommittedNotificationAndClosesCleanly() throws Exception {
        TestUser sender = insertUser("httpSseSender");
        TestUser recipient = insertUser("httpSseRecipient");
        HttpURLConnection stream = connect(jwtTokenProvider.createAccessToken(recipient.id()));
        try {
            assertThat(stream.getResponseCode()).isEqualTo(200);
            assertThat(stream.getContentType()).startsWith("text/event-stream");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream.getInputStream(), StandardCharsets.UTF_8))) {
                assertThat(nextEvent(reader)).contains("event:connected", "data:{}");

                eventId = friendCommands.sendFriendRequest(sender.id(), recipient.nickname())
                        .eventIds().getFirst();
                outboxProcessor.process(eventId);

                assertThat(nextEvent(reader)).contains("event:notification-created", "data:{}");
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM outbox_events WHERE event_id = ?", String.class,
                        eventId.toString())).isEqualTo("COMPLETED");
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE source_event_id = ?", Integer.class,
                        eventId.toString())).isEqualTo(1);

                connections.closeForResynchronization(recipient.id());
                assertThat(reader.readLine()).isNull();
                assertThat(connections.connectionCount(recipient.id())).isZero();
            }
        } finally {
            stream.disconnect();
        }
    }

    @Test
    void unauthenticatedHttpStreamIsRejected() throws Exception {
        HttpURLConnection stream = connect(null);
        try {
            assertThat(stream.getResponseCode()).isEqualTo(401);
        } finally {
            stream.disconnect();
        }
    }

    private HttpURLConnection connect(String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/notifications/stream").toURL().openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        return connection;
    }

    private String nextEvent(BufferedReader reader) throws Exception {
        StringBuilder event = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                return event.toString();
            }
            event.append(line).append('\n');
        }
        return event.toString();
    }

    private TestUser insertUser(String prefix) {
        String nickname = prefix.substring(0, 2) + UUID.randomUUID().toString().substring(0, 8);
        String email = nickname + "@example.test";
        jdbcTemplate.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, email);
        createdUsers.add(userId);
        return new TestUser(userId, nickname);
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbcTemplate.update("DELETE FROM notifications WHERE source_event_id = ?", eventId.toString());
            jdbcTemplate.update("DELETE FROM outbox_events WHERE event_id = ?", eventId.toString());
        }
        for (Long userId : createdUsers) {
            jdbcTemplate.update("DELETE FROM friend_requests WHERE requester_id = ? OR addressee_id = ?",
                    userId, userId);
        }
        for (Long userId : createdUsers) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    private record TestUser(Long id, String nickname) {
    }
}
