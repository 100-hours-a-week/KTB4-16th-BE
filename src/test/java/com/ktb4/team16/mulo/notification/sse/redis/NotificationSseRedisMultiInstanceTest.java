package com.ktb4.team16.mulo.notification.sse.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.MuloApplication;
import com.ktb4.team16.mulo.friend.service.FriendCommandTransactionalService;
import com.ktb4.team16.mulo.global.security.jwt.JwtTokenProvider;
import com.ktb4.team16.mulo.notification.dto.NotificationListResponse;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxProcessor;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import com.ktb4.team16.mulo.notification.sse.NotificationSseConnectionManager;
import io.lettuce.core.KillArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfEnvironmentVariable(named = "MULO_NOTIFICATION_SSE_MULTI_INSTANCE_TEST", matches = "true")
class NotificationSseRedisMultiInstanceTest {

    private static final int REDIS_PORT = 16385;
    private final List<TestUser> users = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<SseStream> streams = new ArrayList<>();
    private ConfigurableApplicationContext first;
    private ConfigurableApplicationContext second;
    private RedisClient adminClient;
    private StatefulRedisConnection<String, String> admin;
    private long firstSubscriptionId;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startInstances() {
        String databaseUrl = System.getenv("DB_URL");
        String expectedDatabase = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))
                ? "3307/mulo" : "3313/mulo_test";
        assertThat(databaseUrl).matches("jdbc:mysql://127\\.0\\.0\\.1:" + expectedDatabase + "(\\?.*)?");
        adminClient = RedisClient.create(RedisURI.Builder.redis("127.0.0.1", REDIS_PORT)
                .withTimeout(Duration.ofSeconds(3)).build());
        admin = adminClient.connect();
        assertThat(subscriptionIds()).as("전용 Redis에 기존 Pub/Sub 구독이 없어야 합니다.").isEmpty();
        first = startInstance("first", databaseUrl);
        assertThat(subscriptionIds()).hasSize(1);
        firstSubscriptionId = subscriptionIds().iterator().next();
        second = startInstance("second", databaseUrl);
        assertThat(subscriptionIds()).hasSize(2);
        assertThat(first.getBean(NotificationCreatedNotifier.class)).isInstanceOf(RedisNotificationCreatedNotifier.class);
        assertThat(second.getBean(NotificationCreatedNotifier.class)).isInstanceOf(RedisNotificationCreatedNotifier.class);
        assertThat(first.getBean(NotificationSseConnectionManager.class))
                .isNotSameAs(second.getBean(NotificationSseConnectionManager.class));
        jdbc = first.getBean(JdbcTemplate.class);
    }

    @Test
    void committedOutboxNotificationsReachBothInstancesAndEveryRecipientTabOnce() throws Exception {
        TestUser sender = insertUser();
        TestUser recipient = insertUser();
        TestUser reverseRecipient = insertUser();
        TestUser unrelated = insertUser();
        SseStream firstTab = connect(first, recipient);
        SseStream secondTab = connect(first, recipient);
        SseStream remoteTab = connect(second, recipient);
        SseStream reverseTab = connect(first, reverseRecipient);
        SseStream unrelatedTab = connect(second, unrelated);

        UUID eventId = friendRequest(first, sender, recipient);
        process(first, eventId);
        process(second, eventId);

        firstTab.expect("notification-created");
        secondTab.expect("notification-created");
        remoteTab.expect("notification-created");
        assertCommitted(eventId);
        for (SseStream stream : streams) {
            stream.expectQuiet();
        }

        UUID reverseEventId = friendRequest(second, sender, reverseRecipient);
        process(second, reverseEventId);

        reverseTab.expect("notification-created");
        assertCommitted(reverseEventId);
        for (SseStream stream : streams) {
            stream.expectQuiet();
        }
        assertThat(unrelatedTab.closed.getCount()).isEqualTo(1);
    }

    @Test
    void rolledBackFriendRequestDoesNotPublishToEitherInstance() throws Exception {
        TestUser sender = insertUser();
        TestUser recipient = insertUser();
        SseStream local = connect(first, recipient);
        SseStream remote = connect(second, recipient);
        TransactionTemplate transaction = new TransactionTemplate(first.getBean(PlatformTransactionManager.class));

        transaction.executeWithoutResult(status -> {
            friendRequest(first, sender, recipient);
            status.setRollbackOnly();
        });

        UUID eventId = eventIds.getFirst();
        process(first, eventId);
        process(second, eventId);

        local.expectQuiet();
        remote.expectQuiet();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_id = ?",
                Integer.class, eventId.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id = ?",
                Integer.class, eventId.toString())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM friend_requests WHERE requester_id = ? AND addressee_id = ?",
                Integer.class, sender.id(), recipient.id())).isZero();
    }

    @Test
    void lostSubscriptionClosesOnlyAffectedStreamsAndRestoresCrossInstanceDelivery() throws Exception {
        TestUser sender = insertUser();
        TestUser recipient = insertUser();
        SseStream firstTab = connect(first, recipient);
        SseStream secondTab = connect(first, recipient);
        SseStream unaffected = connect(second, recipient);

        second.getBean(NotificationCreatedNotifier.class).notificationCreated(Set.of(recipient.id()));
        firstTab.expect("notification-created");
        secondTab.expect("notification-created");
        unaffected.expect("notification-created");

        assertThat(admin.sync().clientKill(KillArgs.Builder.id(firstSubscriptionId))).isEqualTo(1);
        firstTab.expectClosed();
        secondTab.expectClosed();
        assertThat(unaffected.closed.getCount()).isEqualTo(1);

        UUID missedEventId = friendRequest(second, sender, recipient);
        process(second, missedEventId);
        unaffected.expect("notification-created");
        assertCommitted(missedEventId);

        await(() -> {
            synchronized (first.getBean(NotificationSseRedisSubscriber.class)) {
                return !subscriptionIds().contains(firstSubscriptionId)
                        && first.getBean(NotificationSseRedisTransport.class).isSubscriptionHealthy();
            }
        });
        SseStream recovered = connect(first, recipient);
        recovered.expectQuiet();
        assertNotificationVisible(first, recipient, missedEventId);

        second.getBean(NotificationCreatedNotifier.class).notificationCreated(Set.of(recipient.id()));
        recovered.expect("notification-created");
        unaffected.expect("notification-created");
        recovered.expectQuiet();
        unaffected.expectQuiet();
    }

    private ConfigurableApplicationContext startInstance(String name, String databaseUrl) {
        SpringApplication application = new SpringApplication(MuloApplication.class);
        application.setRegisterShutdownHook(false);
        application.setLogStartupInfo(false);
        return application.run(
                "--spring.profiles.active=test",
                "--spring.main.banner-mode=off",
                "--spring.application.name=mulo-sse-multi-instance-" + name,
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--server.shutdown=immediate",
                "--mulo.scheduling.enabled=false",
                "--mulo.daily-song.scheduler.enabled=false",
                "--mulo.rabbitmq.enabled=false",
                "--mulo.notification.sse.mode=redis",
                "--mulo.notification.sse.channel=mulo:test:notification:sse:multi-instance:v1",
                "--mulo.notification.sse.heartbeat-interval=PT1H",
                "--mulo.notification.sse.recovery-check-interval=PT0.1S",
                "--mulo.redis.host=127.0.0.1",
                "--mulo.redis.port=" + REDIS_PORT,
                "--mulo.redis.command-timeout=PT1S");
    }

    private SseStream connect(ConfigurableApplicationContext instance, TestUser user) throws Exception {
        int port = instance.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        String token = instance.getBean(JwtTokenProvider.class).createAccessToken(user.id());
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/notifications/stream").toURL().openConnection();
        SseStream stream = new SseStream(connection, instance.getBean(NotificationSseConnectionManager.class), user.id());
        streams.add(stream);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        assertThat(connection.getResponseCode()).isEqualTo(200);
        assertThat(connection.getContentType()).startsWith("text/event-stream");
        stream.start();
        stream.expect("connected");
        return stream;
    }

    private TestUser insertUser() {
        String nickname = "ss" + UUID.randomUUID().toString().substring(0, 8);
        String email = nickname + "@example.test";
        jdbc.update("INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)",
                email, "test-hash", nickname);
        TestUser user = new TestUser(jdbc.queryForObject(
                "SELECT user_id FROM users WHERE email = ?", Long.class, email), nickname);
        users.add(user);
        return user;
    }

    private UUID friendRequest(ConfigurableApplicationContext instance, TestUser sender, TestUser recipient) {
        UUID eventId = instance.getBean(FriendCommandTransactionalService.class)
                .sendFriendRequest(sender.id(), recipient.nickname()).eventIds().getFirst();
        eventIds.add(eventId);
        return eventId;
    }

    private void process(ConfigurableApplicationContext instance, UUID eventId) {
        instance.getBean(NotificationOutboxProcessor.class).process(eventId);
    }

    private void assertCommitted(UUID eventId) {
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id = ?",
                String.class, eventId.toString())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE source_event_id = ?",
                Integer.class, eventId.toString())).isEqualTo(1);
    }

    private void assertNotificationVisible(ConfigurableApplicationContext instance, TestUser user, UUID eventId)
            throws Exception {
        int port = instance.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/api/notifications").toURL().openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Authorization", "Bearer "
                + instance.getBean(JwtTokenProvider.class).createAccessToken(user.id()));
        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            try (var body = connection.getInputStream()) {
                NotificationListResponse response = instance.getBean(ObjectMapper.class)
                        .readValue(body, NotificationListResponse.class);
                Long notificationId = jdbc.queryForObject(
                        "SELECT notification_id FROM notifications WHERE source_event_id = ?",
                        Long.class, eventId.toString());
                assertThat(response.data().notifications()).extracting(NotificationListResponse.Item::notificationId)
                        .containsExactly(notificationId);
            }
        } finally {
            connection.disconnect();
        }
    }

    private Set<Long> subscriptionIds() {
        Set<Long> ids = new HashSet<>();
        for (String line : admin.sync().clientList().split("\\R")) {
            Map<String, String> fields = new HashMap<>();
            for (String field : line.split(" ")) {
                String[] entry = field.split("=", 2);
                if (entry.length == 2) {
                    fields.put(entry[0], entry[1]);
                }
            }
            if (fields.getOrDefault("flags", "").contains("P")) {
                ids.add(Long.parseLong(fields.get("id")));
            }
        }
        return ids;
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertThat(condition.getAsBoolean()).as("Redis 구독이 제한 시간 안에 복구되어야 합니다.").isTrue();
    }

    @AfterEach
    void cleanUpData() {
        try {
            streams.forEach(SseStream::close);
            for (UUID eventId : eventIds) {
                jdbc.update("DELETE FROM notifications WHERE source_event_id = ?", eventId.toString());
                jdbc.update("DELETE FROM outbox_events WHERE event_id = ?", eventId.toString());
            }
            for (TestUser user : users) {
                jdbc.update("DELETE FROM friend_requests WHERE requester_id = ? OR addressee_id = ?",
                        user.id(), user.id());
            }
            for (TestUser user : users) {
                jdbc.update("DELETE FROM users WHERE user_id = ?", user.id());
            }
        } finally {
            streams.clear();
            eventIds.clear();
            users.clear();
        }
    }

    @AfterAll
    void stopInstances() {
        try {
            if (second != null) {
                second.close();
            }
        } finally {
            try {
                if (first != null) {
                    first.close();
                }
            } finally {
                try {
                    if (admin != null) {
                        admin.close();
                    }
                } finally {
                    if (adminClient != null) {
                        adminClient.shutdown();
                    }
                }
            }
        }
    }

    private record TestUser(Long id, String nickname) {
    }

    private static final class SseStream implements AutoCloseable {

        private final HttpURLConnection connection;
        private final NotificationSseConnectionManager connections;
        private final Long userId;
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<IOException> failure = new AtomicReference<>();

        private SseStream(HttpURLConnection connection, NotificationSseConnectionManager connections, Long userId) {
            this.connection = connection;
            this.connections = connections;
            this.userId = userId;
        }

        private void start() {
            Thread.ofPlatform().daemon().name("notification-sse-multi-instance-reader").start(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder event = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (!event.isEmpty()) {
                                events.add(event.toString());
                                event.setLength(0);
                            }
                        } else if (!line.startsWith(":")) {
                            event.append(line).append('\n');
                        }
                    }
                } catch (IOException exception) {
                    failure.set(exception);
                } finally {
                    closed.countDown();
                }
            });
        }

        private void expect(String name) throws InterruptedException {
            String event = events.poll(5, TimeUnit.SECONDS);
            assertThat(failure.get()).as("SSE 스트림을 읽는 중 오류가 없어야 합니다.").isNull();
            assertThat(event).as("SSE 이벤트 %s를 수신해야 합니다.", name)
                    .isNotNull().contains("event:" + name, "data:{}");
        }

        private void expectQuiet() throws InterruptedException {
            assertThat(events.poll(200, TimeUnit.MILLISECONDS))
                    .as("중복 알림이나 다른 사용자의 알림이 전달되지 않아야 합니다.").isNull();
            assertThat(failure.get()).isNull();
            assertThat(closed.getCount()).as("정상 SSE 연결이 유지되어야 합니다.").isEqualTo(1);
        }

        private void expectClosed() throws InterruptedException {
            assertThat(closed.await(5, TimeUnit.SECONDS)).as("장애가 발생한 SSE 연결이 종료되어야 합니다.").isTrue();
            assertThat(failure.get()).isNull();
            assertThat(events).isEmpty();
        }

        @Override
        public void close() {
            connections.closeForResynchronization(userId);
            connection.disconnect();
        }
    }
}
