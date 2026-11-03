package com.ktb4.team16.mulo.memorysearch.session;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** 공통 Redis에 대화 문맥을 보관하며 사용자별 실행 잠금은 담당하지 않는다. */
public class RedisMemorySearchConversationStore implements MemorySearchConversationStore {
    private static final String SCHEMA_VERSION = "1";
    // 값은 Lua 반환 계약과 동일하게 유지하며 생성 결과와 문맥 갱신 결과를 구분한다.
    private static final Long CREATE_SUCCESS = 1L;
    private static final Long CREATE_KEY_ALREADY_EXISTS = 0L;
    private static final Long UPDATE_SUCCESS = 1L;
    private static final Long CONVERSATION_MISSING = 0L;
    private static final Long CONTEXT_CONFLICT = 2L;
    private static final Long LOCK_OWNERSHIP_LOST = 3L;
    // 통신 실패는 재시도하지 않고 확인된 키 충돌·문맥 경합에만 총 3회까지 시도한다.
    private static final int MAX_ATTEMPTS = 3;
    private static final String CREATE_SCRIPT = loadScript("create-conversation.lua");
    private static final String APPEND_SCRIPT = loadScript("append-conversation.lua");
    private static final String COMPLETE_SCRIPT = loadScript("complete-conversation.lua");
    private final Supplier<StatefulRedisConnection<String, String>> connections;
    private final JsonMapper mapper;
    private final long idleTtlMillis;
    private final MemorySearchRedisKeys keys;

    /** 기존 공통 지연 연결을 사용하며 Redis 접속은 첫 저장소 작업까지 미룬다. */
    public RedisMemorySearchConversationStore(Supplier<StatefulRedisConnection<String, String>> connections,
            JsonMapper mapper, Duration idleTtl, String environment) {
        this.connections = Objects.requireNonNull(connections);
        this.mapper = Objects.requireNonNull(mapper);
        this.idleTtlMillis = Objects.requireNonNull(idleTtl).toMillis();
        if (idleTtlMillis <= 0) {
            throw new IllegalArgumentException("Conversation idle TTL must be at least one millisecond");
        }
        this.keys = new MemorySearchRedisKeys(environment);
    }

    /** 테스트의 독립 연결도 동일한 문맥 계약으로 사용한다. */
    public RedisMemorySearchConversationStore(StatefulRedisConnection<String, String> connection,
            JsonMapper mapper, Duration idleTtl, String environment) {
        this(() -> connection, mapper, idleTtl, environment);
    }

    /** 빈 활성 대화 생성과 TTL을 함께 설정하며 성공을 확인한 UUID만 반환한다. */
    @Override
    public String start(Long userId) {
        return withRedis(() -> {
            RedisCommands<String, String> commands = commands();
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                String conversationId = UUID.randomUUID().toString();
                Long result = commands.eval(CREATE_SCRIPT, ScriptOutputType.INTEGER,
                        new String[] {conversationKey(userId, conversationId)},
                        SCHEMA_VERSION, "[]", Long.toString(idleTtlMillis));
                if (CREATE_SUCCESS.equals(result)) {
                    return conversationId;
                }
                if (!CREATE_KEY_ALREADY_EXISTS.equals(result)) {
                    throw new IllegalStateException("Unexpected conversation creation result");
                }
            }
            throw new IllegalStateException("Could not allocate a unique conversation ID");
        });
    }

    /** 키의 존재만 확인하며 조회로 유휴 만료를 연장하지 않는다. */
    @Override
    public boolean isActive(Long userId, String conversationId) {
        return withRedis(() -> commands().exists(conversationKey(userId, conversationId)) > 0);
    }

    /** 활성 문맥을 읽고 없는 대화와 완료된 질문이 없는 대화를 구분한다. */
    @Override
    public Optional<List<MemorySearchTurn>> read(Long userId, String conversationId) {
        return withRedis(() -> Optional.ofNullable(readSnapshot(commands(), conversationKey(userId, conversationId)))
                .map(Snapshot::turns));
    }

    /** 활성 문맥만 조건부 교체하며 확인된 값 충돌에만 제한적으로 재시도한다. */
    @Override
    public boolean appendIfActive(Long userId, String conversationId, MemorySearchTurn turn) {
        return append(userId, conversationId, turn, null);
    }

    /** 잠금 소유권 확인과 문맥 갱신을 같은 슬롯의 Lua에서 함께 수행한다. */
    @Override
    public boolean appendIfOwned(Long userId, String conversationId, String requestToken, MemorySearchTurn turn) {
        if (requestToken == null || requestToken.isBlank()) {
            throw new IllegalArgumentException("Request owner token is required");
        }
        return append(userId, conversationId, turn, requestToken);
    }

    /** 일반 문맥 추가와 소유권 완료가 같은 직렬화·CAS 경합 정책을 사용하도록 한다. */
    private boolean append(Long userId, String conversationId, MemorySearchTurn turn, String requestToken) {
        Objects.requireNonNull(turn);
        return withRedis(() -> {
            RedisCommands<String, String> commands = commands();
            String key = conversationKey(userId, conversationId);
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                Snapshot snapshot = readSnapshot(commands, key);
                if (snapshot == null) {
                    return false;
                }
                List<MemorySearchTurn> turns = new ArrayList<>(snapshot.turns());
                turns.add(turn);
                if (turns.size() > MemorySearchLimits.MAX_RECENT_TURNS) {
                    turns.removeFirst();
                }
                // JSON은 Java에서 처리해 Lua 숫자 변환으로 Long ID가 손실되지 않게 한다.
                Long result = requestToken == null
                        ? commands.eval(APPEND_SCRIPT, ScriptOutputType.INTEGER,
                                new String[] {key}, snapshot.json(), encodeTurns(turns), Long.toString(idleTtlMillis))
                        : commands.eval(COMPLETE_SCRIPT, ScriptOutputType.INTEGER,
                                new String[] {key, keys.inFlight(userId)}, snapshot.json(), encodeTurns(turns),
                                Long.toString(idleTtlMillis), requestToken);
                if (UPDATE_SUCCESS.equals(result)) {
                    return true;
                }
                if (CONVERSATION_MISSING.equals(result)) {
                    return false;
                }
                if (LOCK_OWNERSHIP_LOST.equals(result)) {
                    throw new IllegalStateException("Memory search request lease is no longer valid");
                }
                if (!CONTEXT_CONFLICT.equals(result)) {
                    throw new IllegalStateException("Unexpected conversation append result");
                }
            }
            throw new IllegalStateException("Conversation update contention exceeded retry limit");
        });
    }

    /** 지정 대화만 삭제하며 별도 사용자 실행 점유권은 해제하지 않는다. */
    @Override
    public void close(Long userId, String conversationId) {
        withRedis(() -> commands().del(conversationKey(userId, conversationId)));
    }

    /** 같은 사용자의 대화 키를 공통 슬롯에 두고 환경·사용자 영역을 분리한다. */
    private String conversationKey(Long userId, String conversationId) {
        return keys.conversation(userId, conversationId);
    }

    /** 두 필드를 함께 읽고 키 부재와 손상된 저장 형식을 구분한다. */
    private Snapshot readSnapshot(RedisCommands<String, String> commands, String key) {
        List<KeyValue<String, String>> fields = commands.hmget(key, "schemaVersion", "turns");
        if (fields.size() != 2) {
            throw new IllegalStateException("Incomplete conversation read result");
        }
        if (!fields.get(0).hasValue() && !fields.get(1).hasValue() && commands.exists(key) == 0) {
            return null;
        }
        if (!fields.get(0).hasValue() || !fields.get(1).hasValue()
                || !SCHEMA_VERSION.equals(fields.get(0).getValue())) {
            throw new IllegalStateException("Unsupported or incomplete conversation payload");
        }
        String json = fields.get(1).getValue();
        return new Snapshot(json, decodeTurns(json));
    }

    /** 완료 문맥을 JSON으로 직렬화하며 전체 AI 응답은 저장하지 않는다. */
    private String encodeTurns(List<MemorySearchTurn> turns) {
        return mapper.writeValueAsString(turns);
    }

    /** 문맥 개수와 불변 턴 계약을 검증하고 Long ID를 그대로 복원한다. */
    private List<MemorySearchTurn> decodeTurns(String json) {
        List<MemorySearchTurn> turns = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, MemorySearchTurn.class));
        if (turns == null || turns.size() > MemorySearchLimits.MAX_RECENT_TURNS || turns.contains(null)) {
            throw new IllegalStateException("Invalid conversation turns");
        }
        return List.copyOf(turns);
    }

    /** 기능이 실제 사용될 때 공통 연결의 제한시간을 따르는 명령 객체를 얻는다. */
    private RedisCommands<String, String> commands() {
        return connections.get().sync();
    }

    /** Redis·변환 실패를 원인과 함께 올리고 만료·빈 문맥으로 위장하지 않는다. */
    private <T> T withRedis(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Memory search conversation store operation failed", exception);
        }
    }

    /** 클래스패스의 짧은 스크립트를 한 번 읽고 누락은 구성 오류로 처리한다. */
    private static String loadScript(String name) {
        try {
            return new ClassPathResource("redis/memory-search/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Conversation script could not be loaded", exception);
        }
    }

    private record Snapshot(String json, List<MemorySearchTurn> turns) { }
}
