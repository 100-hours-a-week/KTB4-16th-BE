package com.ktb4.team16.mulo.memorysearch.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.redis.RedisConfiguration;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** 명시적 opt-in 시 기존 loopback 전용 Redis에서 자신의 키만 사용하는 계약 테스트다. */
@EnabledIfEnvironmentVariable(named = "MULO_MEMORY_SEARCH_REDIS_TEST", matches = "true")
class RedisMemorySearchConversationStoreLocalRedisTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Set<String> ownedKeys = new HashSet<>();
    private RedisClient client;
    private StatefulRedisConnection<String, String> firstConnection;
    private StatefulRedisConnection<String, String> secondConnection;
    private RedisMemorySearchConversationStore first;
    private RedisMemorySearchConversationStore second;
    private String environment;

    /** 두 서버를 흉내 내는 독립 연결을 만들고 테스트별 환경 키를 분리한다. */
    @BeforeEach
    void connectToDedicatedLocalRedis() {
        client = RedisClient.create("redis://127.0.0.1:16382");
        firstConnection = client.connect();
        secondConnection = client.connect();
        environment = "test-" + UUID.randomUUID();
        first = new RedisMemorySearchConversationStore(firstConnection, mapper, Duration.ofMinutes(30), environment);
        second = new RedisMemorySearchConversationStore(secondConnection, mapper, Duration.ofMinutes(30), environment);
    }

    /** 생성한 키만 삭제하고 독립 연결을 닫아 다른 작업의 Redis 데이터를 보존한다. */
    @AfterEach
    void closeOwnedResources() {
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

    /** 생성한 대화는 30분 TTL을 가지며 다른 연결에서 같은 문맥을 읽는다. */
    @Test
    void sharesContextAcrossConnectionsWithThirtyMinuteInitialTtl() {
        String id = start(35L);
        assertThat(firstConnection.sync().pttl(key(35L, id))).isBetween(1_790_000L, 1_800_000L);
        assertThat(second.read(35L, id)).hasValue(List.of());
        var turn = new MemorySearchTurn("비 오는 날 \"노래\"", List.of(Long.MAX_VALUE));
        assertThat(first.appendIfActive(35L, id, turn)).isTrue();
        assertThat(second.read(35L, id)).hasValue(List.of(turn));
    }

    /** 여러 완료 결과에서도 최근 다섯 개만 기존 순서로 유지하고 소유자를 분리한다. */
    @Test
    void keepsLastFiveTurnsAndIsolatesUsersAndConversations() {
        String id = start(35L);
        String another = start(35L);
        for (int index = 1; index <= 6; index++) {
            assertThat(first.appendIfActive(35L, id, new MemorySearchTurn("질문 " + index,
                    List.of((long) index)))).isTrue();
        }
        assertThat(second.read(35L, id).orElseThrow()).extracting(MemorySearchTurn::query)
                .containsExactly("질문 2", "질문 3", "질문 4", "질문 5", "질문 6");
        assertThat(second.read(35L, another)).hasValue(List.of());
        assertThat(second.read(36L, id)).isEmpty();
        second.close(36L, id);
        assertThat(first.isActive(35L, id)).isTrue();
    }

    /** 단순 조회는 줄어든 TTL을 유지하고 완료된 질문만 30분으로 갱신한다. */
    @Test
    void doesNotExtendTtlOnReadAndRefreshesOnlyOnSuccessfulAppend() {
        String id = start(35L);
        firstConnection.sync().pexpire(key(35L, id), 5_000);
        long before = firstConnection.sync().pttl(key(35L, id));
        second.read(35L, id);
        second.isActive(35L, id);
        assertThat(firstConnection.sync().pttl(key(35L, id))).isBetween(1L, before);
        assertThat(first.appendIfActive(35L, id, new MemorySearchTurn("추가", List.of()))).isTrue();
        assertThat(firstConnection.sync().pttl(key(35L, id))).isBetween(1_790_000L, 1_800_000L);
    }

    /** 다른 서버가 종료한 대화를 늦은 완료 결과로 재생성하지 않는다. */
    @Test
    void closedConversationCannotBeAppendedFromAnotherConnection() {
        String id = start(35L);
        second.close(35L, id);
        second.close(35L, id);
        assertThat(first.appendIfActive(35L, id, new MemorySearchTurn("늦은 결과", List.of()))).isFalse();
        assertThat(first.read(35L, id)).isEmpty();
        assertThat(firstConnection.sync().pttl(key(35L, id))).isEqualTo(-2L);
    }

    /** 실제 Redis에서 만료된 대화도 없는 대화로 처리하고 되살리지 않는다. */
    @Test
    void expiredConversationCannotBeResurrected() throws InterruptedException {
        var shortLived = new RedisMemorySearchConversationStore(firstConnection, mapper,
                Duration.ofMillis(80), environment);
        String id = shortLived.start(35L);
        ownedKeys.add(key(35L, id));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (first.isActive(35L, id) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(first.isActive(35L, id)).isFalse();
        assertThat(second.appendIfActive(35L, id, new MemorySearchTurn("늦은 결과", List.of()))).isFalse();
        assertThat(second.read(35L, id)).isEmpty();
    }

    /** 두 연결의 추가가 겹쳐도 어느 한쪽 완료 문맥을 덮어쓰지 않는다. */
    @Test
    void preservesBothConcurrentAppends() throws Exception {
        String id = start(35L);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> {
                start.await();
                return first.appendIfActive(35L, id, new MemorySearchTurn("A", List.of(1L)));
            });
            var right = executor.submit(() -> {
                start.await();
                return second.appendIfActive(35L, id, new MemorySearchTurn("B", List.of(2L)));
            });
            start.countDown();
            assertThat(left.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(right.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(first.read(35L, id).orElseThrow()).extracting(MemorySearchTurn::query)
                .containsExactlyInAnyOrder("A", "B");
    }

    /** 실제 Lua의 stale snapshot 거절은 최신 문맥과 기존 TTL을 보존한다. */
    @Test
    void staleSnapshotDoesNotOverwriteContextOrExtendTtl() throws Exception {
        String id = start(35L);
        first.appendIfActive(35L, id, new MemorySearchTurn("최신", List.of(Long.MAX_VALUE)));
        firstConnection.sync().pexpire(key(35L, id), 5_000);
        long before = firstConnection.sync().pttl(key(35L, id));
        String script = new ClassPathResource("redis/memory-search/append-conversation.lua")
                .getContentAsString(StandardCharsets.UTF_8);
        Long result = secondConnection.sync().eval(script, ScriptOutputType.INTEGER,
                new String[] {key(35L, id)}, "[]", "[]", "1800000");
        assertThat(result).isEqualTo(2L);
        assertThat(first.read(35L, id).orElseThrow()).extracting(MemorySearchTurn::query).containsExactly("최신");
        assertThat(firstConnection.sync().pttl(key(35L, id))).isBetween(1L, before);
        second.close(35L, id);
        Long gone = firstConnection.sync().eval(script, ScriptOutputType.INTEGER,
                new String[] {key(35L, id)}, "[]", "[]", "1800000");
        assertThat(gone).isZero();
        assertThat(firstConnection.sync().exists(key(35L, id))).isZero();
    }

    /** 인기곡 기능이 꺼진 실제 공통 연결에서도 문맥 저장·종료가 작동한다. */
    @Test
    void sharedConnectionWorksWithoutPopularCache() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(RedisConfiguration.class,
                MemorySearchConversationConfiguration.class, InMemoryMemorySearchConversationStore.class)
                .withBean(JsonMapper.class, () -> mapper)
                .withPropertyValues("mulo.redis.host=127.0.0.1", "mulo.redis.port=16382",
                        "memory-search.conversation-store=redis", "memory-search.conversation-idle-ttl=30m",
                        "memory-search.conversation-environment=" + environment,
                        "mulo.popular-cache.enabled=false")
                .run(context -> {
                    MemorySearchConversationStore store = context.getBean(MemorySearchConversationStore.class);
                    String id = store.start(35L);
                    ownedKeys.add(key(35L, id));
                    assertThat(second.read(35L, id)).hasValue(List.of());
                    store.close(35L, id);
                    assertThat(first.isActive(35L, id)).isFalse();
                });
    }

    /** 자신이 만든 대화만 종료 후 정리 목록에 등록한다. */
    private String start(Long userId) {
        String id = first.start(userId);
        ownedKeys.add(key(userId, id));
        return id;
    }

    /** 테스트 저장소와 동일한 환경·사용자 키를 관리용으로 만든다. */
    private String key(Long userId, String id) {
        return "mulo:memory-search:{" + environment + ":u:" + userId + "}:conversation:" + id;
    }
}
