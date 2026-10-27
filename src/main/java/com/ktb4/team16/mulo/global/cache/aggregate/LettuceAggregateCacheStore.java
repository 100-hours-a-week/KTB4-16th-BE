package com.ktb4.team16.mulo.global.cache.aggregate;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.function.Supplier;

/** 명시적으로 구성한 Lettuce 연결을 최소 문자열 저장소 계약에 맞춘다. */
public class LettuceAggregateCacheStore implements AggregateCacheStore {
    private static final String RELEASE_LOCK = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;
    private final Supplier<StatefulRedisConnection<String, String>> connections;

    /** 연결 생성을 지연하여 Redis 미기동 중에도 앱의 DB 경로를 사용할 수 있게 한다. */
    public LettuceAggregateCacheStore(Supplier<StatefulRedisConnection<String, String>> connections) {
        this.connections = connections;
    }

    /** 테스트 또는 명시적으로 열린 연결을 동일한 저장소 계약으로 사용한다. */
    public LettuceAggregateCacheStore(StatefulRedisConnection<String, String> connection) {
        this(() -> connection);
    }

    /** 문자열 값을 읽고 Redis의 없는 키는 null로 유지한다. */
    @Override
    public String get(String key) {
        return commands().get(key);
    }

    /** 일반 payload에는 밀리초 TTL을 주고 ZERO인 세대 키만 무기한 저장한다. */
    @Override
    public void set(String key, String value, Duration ttl) {
        if (ttl.isZero()) {
            commands().set(key, value);
        } else {
            commands().set(key, value, new SetArgs().px(ttl.toMillis()));
        }
    }

    /** NX와 PX를 한 명령으로 적용하여 세대 초기화나 유한 잠금을 획득한다. */
    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        SetArgs arguments = new SetArgs().nx();
        if (!ttl.isZero()) {
            arguments.px(ttl.toMillis());
        }
        return "OK".equals(commands().set(key, value, arguments));
    }

    /** Lua에서 소유 토큰 비교와 삭제를 원자적으로 실행해 후속 소유자의 잠금을 보존한다. */
    @Override
    public void releaseLock(String key, String token) {
        commands().eval(RELEASE_LOCK, ScriptOutputType.INTEGER, new String[] {key}, token);
    }

    /** 필요할 때만 연결을 얻고 동기 명령의 제한시간은 명시한 클라이언트 설정을 따른다. */
    private RedisCommands<String, String> commands() {
        return connections.get().sync();
    }
}
