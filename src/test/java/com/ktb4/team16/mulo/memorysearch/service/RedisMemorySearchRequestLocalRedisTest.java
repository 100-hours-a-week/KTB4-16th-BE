package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.ai.MemorySearchAiGateway;
import com.ktb4.team16.mulo.memorysearch.ai.MemorySearchAiResult;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchRedisKeys;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import com.ktb4.team16.mulo.memorysearch.session.RedisMemorySearchConversationStore;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/** 독립 Redis 연결 두 개로만 서버 경합을 재현하며 대규모 부하는 발생시키지 않는다. */
@EnabledIfEnvironmentVariable(named = "MULO_MEMORY_SEARCH_REDIS_TEST", matches = "true")
class RedisMemorySearchRequestLocalRedisTest {
    private final Set<String> ownedKeys = new HashSet<>();
    private RedisClient client;
    private StatefulRedisConnection<String, String> firstConnection;
    private StatefulRedisConnection<String, String> secondConnection;
    private RedisMemorySearchConversationStore firstStore;
    private RedisMemorySearchConversationStore secondStore;
    private RedisMemorySearchInFlightRegistry firstRegistry;
    private RedisMemorySearchInFlightRegistry secondRegistry;
    private MemorySearchRedisKeys keys;
    private String environment;

    /** 전용 loopback Redis에 두 연결을 열고 테스트마다 고유한 키 영역을 사용한다. */
    @BeforeEach
    void connect() {
        client = RedisClient.create("redis://127.0.0.1:16382");
        firstConnection = client.connect();
        secondConnection = client.connect();
        environment = "lock-test-" + UUID.randomUUID();
        keys = new MemorySearchRedisKeys(environment);
        var mapper = JsonMapper.builder().build();
        firstStore = new RedisMemorySearchConversationStore(firstConnection, mapper, Duration.ofMinutes(30), environment);
        secondStore = new RedisMemorySearchConversationStore(secondConnection, mapper, Duration.ofMinutes(30), environment);
        firstRegistry = registry(firstConnection, Duration.ofSeconds(60));
        secondRegistry = registry(secondConnection, Duration.ofSeconds(60));
        ownedKeys.add(keys.inFlight(35L));
        ownedKeys.add(keys.inFlight(36L));
    }

    /** 생성한 문맥·잠금 키만 지우고 다른 기능의 Redis 데이터는 보존한다. */
    @AfterEach
    void closeResources() {
        if (firstConnection != null) {
            for (String key : ownedKeys) {
                firstConnection.sync().del(key);
            }
            firstConnection.close();
        }
        if (secondConnection != null) {
            secondConnection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    /** 두 서버의 동시 NX 시도에서 같은 사용자 점유권은 하나만 발급한다. */
    @Test
    void concurrentServersAcquireExactlyOneLease() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> {
                start.await();
                return firstRegistry.tryAcquire(35L);
            });
            var right = executor.submit(() -> {
                start.await();
                return secondRegistry.tryAcquire(35L);
            });
            start.countDown();
            var a = left.get(5, TimeUnit.SECONDS);
            var b = right.get(5, TimeUnit.SECONDS);
            assertThat(a.isPresent() ^ b.isPresent()).isTrue();
            assertThat(firstConnection.sync().pttl(keys.inFlight(35L))).isBetween(50_000L, 60_000L);
            try (var anotherUser = secondRegistry.tryAcquire(36L).orElseThrow()) {
                assertThat(anotherUser).isNotNull();
            }
            a.ifPresent(MemorySearchRequestLease::close);
            b.ifPresent(MemorySearchRequestLease::close);
        }
        assertThat(secondConnection.sync().exists(keys.inFlight(35L))).isZero();
    }

    /** AI 대기 중인 사용자는 다른 서버·다른 대화에서도 AI 호출 전에 409를 받는다. */
    @Test
    void runningAiBlocksSameUserOnAnotherServerBeforeAiInvocation() throws Exception {
        String id = startConversation();
        String another = startConversation();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var callsOnSecondServer = new AtomicInteger();
        MemorySearchAiGateway firstAi = (userId, query, history) -> {
            entered.countDown();
            await(finish);
            return emptyAnswer();
        };
        MemorySearchAiGateway secondAi = (userId, query, history) -> {
            callsOnSecondServer.incrementAndGet();
            return emptyAnswer();
        };
        var firstService = service(firstStore, firstRegistry, firstAi);
        var secondService = service(secondStore, secondRegistry, secondAi);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var active = executor.submit(() -> firstService.search(35L, id, "첫 질문"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> secondService.search(35L, another, "중복 질문"))
                    .isInstanceOfSatisfying(MemorySearchDomainException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.MEMORY_SEARCH_IN_PROGRESS));
            assertThat(callsOnSecondServer).hasValue(0);
            finish.countDown();
            assertThat(active.get(5, TimeUnit.SECONDS).answer()).isEqualTo("응답");
            assertThat(secondStore.read(35L, id).orElseThrow()).extracting(MemorySearchTurn::query)
                    .containsExactly("첫 질문");
            secondService.search(35L, another, "다음 질문");
            assertThat(callsOnSecondServer).hasValue(1);
        } finally {
            finish.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 만료된 소유자는 후속 요청의 문맥과 잠금을 건드리지 못하며 현재 요청만 완료한다. */
    @Test
    void expiredLeaseCannotWriteOrReleaseSuccessorsLock() throws Exception {
        String id = startConversation();
        var old = registry(firstConnection, Duration.ofMillis(80)).tryAcquire(35L).orElseThrow();
        awaitExpiry();
        var current = secondRegistry.tryAcquire(35L).orElseThrow();
        String currentToken = secondConnection.sync().get(keys.inFlight(35L));
        assertThatThrownBy(() -> old.appendIfActive(firstStore, id, new MemorySearchTurn("옛 결과", List.of())))
                .isInstanceOf(IllegalStateException.class);
        old.close();
        old.close();
        assertThat(secondConnection.sync().get(keys.inFlight(35L))).isEqualTo(currentToken);
        assertThat(secondStore.read(35L, id)).hasValue(List.of());
        assertThat(current.appendIfActive(secondStore, id, new MemorySearchTurn("현재 결과", List.of()))).isTrue();
        current.close();
        assertThat(firstStore.read(35L, id).orElseThrow()).extracting(MemorySearchTurn::query)
                .containsExactly("현재 결과");
    }

    /** 뒤따르는 요청이 없어도 TTL을 잃은 요청은 자동 재획득하거나 완료하지 않는다. */
    @Test
    void expiredLeaseWithoutSuccessorCannotComplete() throws Exception {
        String id = startConversation();
        var old = registry(firstConnection, Duration.ofMillis(80)).tryAcquire(35L).orElseThrow();
        awaitExpiry();
        assertThatThrownBy(() -> old.appendIfActive(firstStore, id, new MemorySearchTurn("늦은 응답", List.of())))
                .isInstanceOf(IllegalStateException.class);
        old.close();
        assertThat(secondStore.read(35L, id)).hasValue(List.of());
        assertThat(firstConnection.sync().exists(keys.inFlight(35L))).isZero();
    }

    /** 실제 서비스에서 지연된 AI가 돌아와도 만료 후 새 소유자의 점유권은 보존한다. */
    @Test
    void lateAiResponseIsRejectedByServiceAndDoesNotReleaseNewOwner() throws Exception {
        String id = startConversation();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var firstService = service(firstStore, firstRegistry, (userId, query, history) -> {
            entered.countDown();
            await(finish);
            return emptyAnswer();
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var old = executor.submit(() -> firstService.search(35L, id, "늦은 질문"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            firstConnection.sync().pexpire(keys.inFlight(35L), 1);
            awaitExpiry();
            try (var current = secondRegistry.tryAcquire(35L).orElseThrow()) {
                String token = secondConnection.sync().get(keys.inFlight(35L));
                finish.countDown();
                assertThatThrownBy(() -> old.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasRootCauseMessage("Memory search request lease is no longer valid");
                assertThat(secondConnection.sync().get(keys.inFlight(35L))).isEqualTo(token);
                assertThat(secondStore.read(35L, id)).hasValue(List.of());
            }
        } finally {
            finish.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 대화 종료는 실행 중인 사용자 잠금을 해제하지 않고 늦은 결과도 저장하지 않는다. */
    @Test
    void closingConversationRetainsLockAndRejectsCompletion() {
        String id = startConversation();
        var lease = firstRegistry.tryAcquire(35L).orElseThrow();
        secondStore.close(35L, id);
        assertThat(secondRegistry.tryAcquire(35L)).isEmpty();
        assertThat(lease.appendIfActive(firstStore, id, new MemorySearchTurn("결과", List.of()))).isFalse();
        lease.close();
        assertThat(firstStore.read(35L, id)).isEmpty();
        try (var next = secondRegistry.tryAcquire(35L).orElseThrow()) {
            lease.close();
            assertThat(firstRegistry.tryAcquire(35L)).isEmpty();
        }
    }

    /** AI 실패 후에도 기존 try-with-resources 경로가 자기 잠금을 해제한다. */
    @Test
    void aiFailureReleasesLeaseForNextServer() {
        String id = startConversation();
        var service = service(firstStore, firstRegistry, (userId, query, history) -> {
            throw new IllegalStateException("AI failure");
        });
        assertThatThrownBy(() -> service.search(35L, id, "질문"))
                .isInstanceOf(IllegalStateException.class).hasMessage("AI failure");
        try (var next = secondRegistry.tryAcquire(35L).orElseThrow()) {
            assertThat(next).isNotNull();
        }
        assertThat(secondStore.read(35L, id)).hasValue(List.of());
    }

    /** 실제 잠금·문맥과 가짜 AI·기록 저장소로 외부 과금 없이 서비스 흐름을 검증한다. */
    private MemorySearchService service(RedisMemorySearchConversationStore store,
            MemorySearchRequestRegistry registry, MemorySearchAiGateway ai) {
        RecordRepository repository = mock(RecordRepository.class);
        when(repository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(35L)).thenReturn(true);
        return new MemorySearchService(store, new MemorySearchRequestGate(store, registry),
                new MemorySearchRecordAvailability(repository), new MemorySearchOwnedRecordFilter(repository),
                repository, ai);
    }

    /** 연결별 저장소가 동일 환경 키와 지정된 테스트 잠금 TTL을 사용하도록 한다. */
    private RedisMemorySearchInFlightRegistry registry(StatefulRedisConnection<String, String> connection,
            Duration ttl) {
        return new RedisMemorySearchInFlightRegistry(() -> connection, ttl, environment);
    }

    /** 대화 생성 키를 정리 목록에 등록한다. */
    private String startConversation() {
        String id = firstStore.start(35L);
        ownedKeys.add(keys.conversation(35L, id));
        return id;
    }

    /** 실제 서버 TTL 만료를 제한 시간 안에서 관찰하며 긴 부하를 발생시키지 않는다. */
    private void awaitExpiry() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (firstConnection.sync().exists(keys.inFlight(35L)) > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(firstConnection.sync().exists(keys.inFlight(35L))).isZero();
    }

    /** AI 대기를 테스트가 제어하되 실패 시에도 무한히 작업을 붙잡지 않는다. */
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test AI wait timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test AI interrupted", exception);
        }
    }

    /** 기록 조회가 추가로 필요 없는 고정 AI 응답을 반환한다. */
    private MemorySearchAiResult emptyAnswer() {
        return new MemorySearchAiResult("응답", List.of(), List.of());
    }
}
