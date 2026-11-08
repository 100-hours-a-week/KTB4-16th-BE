package com.ktb4.team16.mulo.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import io.sentry.Sentry;
import java.io.IOException;
import java.net.SocketException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseConnectionManagerTest {
    private NotificationSseConnectionManager connections;

    @AfterEach
    void closeConnections() {
        if (connections != null) {
            connections.shutdown();
        }
    }

    @Test
    void connectedAndCreatedEventsReachEveryTabButNotAnotherUser() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMinutes(1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        MvcResult first = connect(mvc, 1L, 30);
        MvcResult second = connect(mvc, 1L, 30);
        MvcResult other = connect(mvc, 2L, 30);

        connections.sendNotificationCreated(1L);

        assertThat(first.getResponse().getContentAsString())
                .contains("event:connected", "event:notification-created", "data:{}");
        assertThat(second.getResponse().getContentAsString())
                .contains("event:connected", "event:notification-created", "data:{}");
        assertThat(other.getResponse().getContentAsString())
                .contains("event:connected").doesNotContain("event:notification-created");
        assertThat(connections.connectionCount(1L)).isEqualTo(2);
        assertThat(connections.connectionCount(2L)).isEqualTo(1);
    }

    @Test
    void heartbeatUsesCommentFrame() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofMillis(30), Duration.ofSeconds(2));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        MvcResult result = connect(mvc, 1L, 30);

        awaitText(result, ":heartbeat", 1000);

        assertThat(result.getResponse().getContentAsString()).doesNotContain("event:heartbeat");
    }

    @Test
    void tokenExpirySendsControlEventAndRemovesConnection() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofSeconds(5));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        MvcResult result = connect(mvc, 1L, 1);

        awaitText(result, "event:auth-expired", 2500);

        assertThat(connections.connectionCount(1L)).isZero();
    }

    @Test
    void maximumConnectionDurationRemovesConnectionWithoutAuthExpiryEvent() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMillis(80));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        MvcResult result = connect(mvc, 1L, 30);

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (connections.connectionCount(1L) != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(connections.connectionCount(1L)).isZero();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("event:auth-expired");
    }

    @Test
    void completedConnectionIsRemoved() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMinutes(1));
        TestStreamController controller = new TestStreamController(connections);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MvcResult result = connect(mvc, 1L, 30);

        controller.lastEmitter.complete();
        mvc.perform(asyncDispatch(result));
        assertThat(connections.connectionCount(1L)).isZero();
    }

    @Test
    void rejectedSubmissionClosesOnlyAffectedUsersConnections() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMinutes(1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        connect(mvc, 1L, 30);
        connect(mvc, 1L, 30);
        connect(mvc, 2L, 30);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> { });

            new LocalNotificationCreatedNotifier(connections, executor).notificationCreated(Set.of(1L));

            assertThat(connections.connectionCount(1L)).isZero();
            assertThat(connections.connectionCount(2L)).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void closingForResynchronizationRacesSafelyWithSending() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMinutes(1));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        connect(mvc, 1L, 30);
        connect(mvc, 1L, 30);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var send = workers.submit(() -> connections.sendNotificationCreated(1L));
            var close = workers.submit(() -> connections.closeForResynchronization(1L));
            send.get(2, TimeUnit.SECONDS);
            close.get(2, TimeUnit.SECONDS);
            assertThat(connections.connectionCount(1L)).isZero();
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void failedConnectionDoesNotPreventAnotherTabFromReceivingSignal() throws Exception {
        AtomicInteger created = new AtomicInteger();
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMinutes(1)) {
            @Override
            protected SseEmitter createEmitter(long timeoutMillis) {
                return created.getAndIncrement() == 0
                        ? new FailingEmitter(timeoutMillis) : new SseEmitter(timeoutMillis);
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        connect(mvc, 1L, 30);
        MvcResult healthy = connect(mvc, 1L, 30);

        connections.sendNotificationCreated(1L);

        assertThat(healthy.getResponse().getContentAsString()).contains("event:notification-created");
        assertThat(connections.connectionCount(1L)).isEqualTo(1);
    }

    @Test
    void heartbeatFailureRemovesConnection() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofMillis(30), Duration.ofMinutes(1)) {
            @Override
            protected SseEmitter createEmitter(long timeoutMillis) {
                return new FailingEmitter(timeoutMillis);
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestStreamController(connections)).build();
        connect(mvc, 1L, 30);

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (connections.connectionCount(1L) != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(connections.connectionCount(1L)).isZero();
    }

    @Test
    void onErrorRemovesConnectionWithoutCompletingEmitter() {
        TrackingConnectionManager manager = new TrackingConnectionManager(false);
        connections = manager;
        TrackingEmitter emitter = manager.connectTracked(1L);

        emitter.signalError(new AsyncRequestNotUsableException("Response not usable after response errors."));
        emitter.signalCompletion();

        assertThat(emitter.completeCalls).isZero();
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void onCompletionRemovesConnectionWithoutCompletingEmitterAgain() {
        TrackingConnectionManager manager = new TrackingConnectionManager(false);
        connections = manager;
        TrackingEmitter emitter = manager.connectTracked(1L);

        emitter.signalCompletion();

        assertThat(emitter.completeCalls).isZero();
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void timeoutCompletesOnceAndLaterCompletionCallbackDoesNotRepeatIt() {
        TrackingConnectionManager manager = new TrackingConnectionManager(false);
        connections = manager;
        TrackingEmitter emitter = manager.connectTracked(1L);

        emitter.signalTimeout();
        emitter.signalCompletion();

        assertThat(emitter.completeCalls).isEqualTo(1);
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void applicationCloseCompletesEmitterAndRemovesConnection() {
        TrackingConnectionManager manager = new TrackingConnectionManager(false);
        connections = manager;
        TrackingEmitter emitter = manager.connectTracked(1L);

        manager.closeForResynchronization(1L);

        assertThat(emitter.completeCalls).isEqualTo(1);
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void repeatedContainerCallbacksCleanUpConnectionOnlyOnce() {
        TrackingConnectionManager manager = new TrackingConnectionManager(false);
        connections = manager;
        TrackingEmitter emitter = manager.connectTracked(1L);

        emitter.signalError(new AsyncRequestNotUsableException("응답을 더 이상 사용할 수 없습니다."));
        emitter.signalCompletion();
        emitter.signalError(new IOException("연결 종료"));

        assertThat(emitter.completeCalls).isZero();
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void oldConnectionCallbackDoesNotRemoveAnotherTabConnection() {
        TrackingConnectionManager manager = new TrackingConnectionManager(false);
        connections = manager;
        TrackingEmitter oldEmitter = manager.connectTracked(1L);
        TrackingEmitter newEmitter = manager.connectTracked(1L);

        oldEmitter.signalCompletion();

        assertThat(manager.connectionCount(1L)).isEqualTo(1);
        assertThat(newEmitter.completeCalls).isZero();
        newEmitter.signalCompletion();
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void eventSendFailureRemovesConnectionWithoutCompletingFailedResponse() {
        TrackingConnectionManager manager = new TrackingConnectionManager(true);
        connections = manager;
        TrackingEmitter emitter = manager.connectTracked(1L);

        manager.sendNotificationCreated(1L);

        assertThat(emitter.completeCalls).isZero();
        assertThat(manager.connectionCount(1L)).isZero();
    }

    @Test
    void clientDisconnectIsNotCapturedTwiceAcrossSseAndMvcHandling() {
        var failure = new AsyncRequestNotUsableException("쓰기 실패",
                new SocketException("Broken pipe"));
        TrackingConnectionManager manager = new TrackingConnectionManager(false, failure);
        connections = manager;
        manager.connectTracked(1L);

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            manager.sendNotificationCreated(1L);

            assertThat(manager.connectionCount(1L)).isZero();
            sentry.verifyNoInteractions();

            new GlobalExceptionHandler().handleAsyncRequestNotUsable(failure);

            sentry.verifyNoInteractions();
        }
    }

    @Test
    void unknownAsyncWriteCauseIsDeferredToMvcForSingleSentryCapture() {
        IOException originalCause = new IOException("unexpected socket write failure");
        var failure = new AsyncRequestNotUsableException("쓰기 실패", originalCause);
        TrackingConnectionManager manager = new TrackingConnectionManager(false, failure);
        connections = manager;
        manager.connectTracked(1L);

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            manager.sendNotificationCreated(1L);

            assertThat(manager.connectionCount(1L)).isZero();
            sentry.verifyNoInteractions();

            new GlobalExceptionHandler().handleAsyncRequestNotUsable(failure);

            assertThat(failure.getCause()).isSameAs(originalCause);
            sentry.verify(() -> Sentry.captureException(failure));
            sentry.verifyNoMoreInteractions();
        }
    }

    @Test
    void unusableSseResponseDoesNotAttemptJsonErrorBody() throws Exception {
        connections = new NotificationSseConnectionManager(Clock.systemUTC(),
                Duration.ofSeconds(2), Duration.ofMinutes(1));
        TestStreamController controller = new TestStreamController(connections);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        MvcResult result = connect(mvc, 1L, 30);

        controller.lastEmitter.completeWithError(
                new AsyncRequestNotUsableException("Response not usable after response errors."));

        MvcResult dispatched = mvc.perform(asyncDispatch(result))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        assertThat(dispatched.getResponse().getContentAsString()).doesNotContain("INTERNAL_SERVER_ERROR");
    }

    private MvcResult connect(MockMvc mvc, long userId, int tokenLifetimeSeconds) throws Exception {
        return mvc.perform(get("/test/stream").param("userId", Long.toString(userId))
                        .param("expiresAt", Instant.now().plusSeconds(tokenLifetimeSeconds).toString()))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private void awaitText(MvcResult result, String expected, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (!result.getResponse().getContentAsString().contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(result.getResponse().getContentAsString()).contains(expected);
    }

    @RestController
    private static final class TestStreamController {
        private final NotificationSseConnectionManager connections;
        private SseEmitter lastEmitter;

        private TestStreamController(NotificationSseConnectionManager connections) {
            this.connections = connections;
        }

        @GetMapping(value = "/test/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream(@RequestParam Long userId, @RequestParam Instant expiresAt) {
            lastEmitter = connections.connect(userId, expiresAt);
            return lastEmitter;
        }
    }

    private static final class FailingEmitter extends SseEmitter {
        private int sendCount;

        private FailingEmitter(long timeoutMillis) {
            super(timeoutMillis);
        }

        @Override
        public void send(SseEventBuilder event) throws IOException {
            if (++sendCount > 1) {
                throw new IOException("SSE 연결이 끊어졌습니다.");
            }
            super.send(event);
        }
    }

    private static final class TrackingConnectionManager extends NotificationSseConnectionManager {
        private final boolean failOnEventSend;
        private TrackingEmitter lastEmitter;

        private TrackingConnectionManager(boolean failOnEventSend) {
            this(failOnEventSend, null);
        }

        private TrackingConnectionManager(boolean failOnEventSend, IOException sendFailure) {
            super(Clock.systemUTC(), Duration.ofHours(1), Duration.ofHours(2));
            this.failOnEventSend = failOnEventSend;
            this.sendFailure = sendFailure;
        }

        private final IOException sendFailure;

        @Override
        protected SseEmitter createEmitter(long timeoutMillis) {
            lastEmitter = new TrackingEmitter(timeoutMillis, failOnEventSend, sendFailure);
            return lastEmitter;
        }

        private TrackingEmitter connectTracked(Long userId) {
            connect(userId, Instant.now().plusSeconds(3600));
            return lastEmitter;
        }
    }

    private static final class TrackingEmitter extends SseEmitter {
        private final boolean failOnEventSend;
        private final IOException sendFailure;
        private Runnable completionCallback;
        private Runnable timeoutCallback;
        private Consumer<Throwable> errorCallback;
        private int sendCalls;
        private int completeCalls;

        private TrackingEmitter(long timeoutMillis, boolean failOnEventSend, IOException sendFailure) {
            super(timeoutMillis);
            this.failOnEventSend = failOnEventSend;
            this.sendFailure = sendFailure;
        }

        @Override
        public void onCompletion(Runnable callback) {
            completionCallback = callback;
            super.onCompletion(callback);
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallback = callback;
            super.onTimeout(callback);
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallback = callback;
            super.onError(callback);
        }

        @Override
        public void send(SseEventBuilder event) throws IOException {
            sendCalls++;
            if (sendFailure != null && sendCalls > 1) {
                throw sendFailure;
            }
            if (failOnEventSend && sendCalls > 1) {
                throw new AsyncRequestNotUsableException("Response not usable after response errors.");
            }
            super.send(event);
        }

        @Override
        public void complete() {
            completeCalls++;
            super.complete();
        }

        private void signalCompletion() {
            completionCallback.run();
        }

        private void signalTimeout() {
            timeoutCallback.run();
        }

        private void signalError(Throwable exception) {
            errorCallback.accept(exception);
        }
    }
}
