package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchRedisKeys;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/** SET NX PX로 서버 전체의 사용자별 점유권을 관리하며 Pub/Sub을 사용하지 않는다. */
public final class RedisMemorySearchInFlightRegistry implements MemorySearchRequestRegistry {
    private static final Logger log = LoggerFactory.getLogger(RedisMemorySearchInFlightRegistry.class);
    private static final String RELEASE_SCRIPT = loadReleaseScript();
    private final Supplier<StatefulRedisConnection<String, String>> connections;
    private final long lockTtlMillis;
    private final MemorySearchRedisKeys keys;

    /** 공통 지연 연결과 유한 잠금 시간을 사용하고 문맥 TTL과 구분한다. */
    public RedisMemorySearchInFlightRegistry(Supplier<StatefulRedisConnection<String, String>> connections,
            Duration lockTtl, String environment) {
        this.connections = Objects.requireNonNull(connections);
        this.lockTtlMillis = Objects.requireNonNull(lockTtl).toMillis();
        if (lockTtlMillis <= 0) {
            throw new IllegalArgumentException("Request lock TTL must be at least one millisecond");
        }
        this.keys = new MemorySearchRedisKeys(environment);
    }

    /** NX 실패만 busy로 반환하며 불명확한 통신 결과로 AI를 실행하거나 재획득하지 않는다. */
    @Override
    public Optional<MemorySearchRequestLease> tryAcquire(Long userId) {
        String token = UUID.randomUUID().toString();
        String key = keys.inFlight(userId);
        try {
            String result = connections.get().sync().set(key, token, new SetArgs().nx().px(lockTtlMillis));
            if (result == null) {
                return Optional.empty();
            }
            if (!"OK".equals(result)) {
                throw new IllegalStateException("Unexpected request lock acquisition result");
            }
            return Optional.of(new RedisLease(userId, key, token));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Memory search request lock acquisition failed", exception);
        }
    }

    /** 고유 토큰을 숨긴 채 소유권 기반 완료와 한 번의 안전한 해제를 제공한다. */
    private final class RedisLease implements MemorySearchRequestLease {
        private final Long userId;
        private final String key;
        private final String token;
        private final AtomicBoolean closed = new AtomicBoolean();

        /** 획득된 사용자·키·토큰을 요청 수명 동안 보관한다. */
        private RedisLease(Long userId, String key, String token) {
            this.userId = userId;
            this.key = key;
            this.token = token;
        }

        /** Redis 스크립트에서 현재 소유권과 문맥 갱신을 함께 검사하도록 위임한다. */
        @Override
        public boolean appendIfActive(MemorySearchConversationStore store, String conversationId,
                MemorySearchTurn turn) {
            if (closed.get()) {
                throw new IllegalStateException("Memory search request lease is already closed");
            }
            return store.appendIfOwned(userId, conversationId, token, turn);
        }

        /** 자기 토큰만 삭제하며 해제 실패는 기록하고 TTL에 맡겨 성공 응답을 보존한다. */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                connections.get().sync().eval(RELEASE_SCRIPT, ScriptOutputType.INTEGER,
                        new String[] {key}, token);
            } catch (RuntimeException exception) {
                // 사용자 질문·토큰은 로그에 남기지 않고 추가 해제 재시도도 하지 않는다.
                log.warn("Memory search request lock release failed; awaiting TTL ({})",
                        exception.getClass().getSimpleName());
            }
        }
    }

    /** 토큰 확인과 삭제를 원자적으로 수행할 스크립트를 한 번 로딩한다. */
    private static String loadReleaseScript() {
        try {
            return new ClassPathResource("redis/memory-search/release-request.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Request release script could not be loaded", exception);
        }
    }
}
