package com.ktb4.team16.mulo.global.security.session;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 기존 지연 Lettuce 연결로 만료되는 로그아웃 표식을 보관한다. */
@Component
public final class RedisSessionRevocationStore implements SessionRevocationStore {
    private static final String SESSION_KEY_PREFIX = "mulo:security:revoked-session:";
    private static final String LEGACY_USER_KEY_PREFIX = "mulo:security:legacy-user-revoked:";
    private static final long REVOKE_SESSION_SCRIPT_SUCCESS_RESULT = 1L;
    private static final String REVOKE_SESSION_SCRIPT = loadRevokeSessionScript();

    private final ObjectProvider<StatefulRedisConnection<String, String>> connections;

    /** 공통 Redis 연결을 요청될 때만 가져오도록 provider를 보관한다. */
    public RedisSessionRevocationStore(ObjectProvider<StatefulRedisConnection<String, String>> connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    /** 두 만료 표식을 하나의 Lua 실행으로 기록하고 Redis 오류는 호출자에게 전달한다. */
    @Override
    public void revokeSession(String sessionId, Duration sessionTtl, Long legacyUserId, Duration legacyTtl) {
        String sessionKey = sessionKey(sessionId);
        String legacyUserKey = legacyUserKey(legacyUserId);
        long sessionTtlMillis = positiveTtlMillis(sessionTtl, "session");
        long legacyTtlMillis = positiveTtlMillis(legacyTtl, "legacy user");

        Long result = commands().eval(REVOKE_SESSION_SCRIPT, ScriptOutputType.INTEGER,
                new String[] {sessionKey, legacyUserKey},
                Long.toString(sessionTtlMillis), Long.toString(legacyTtlMillis));
        if (!Long.valueOf(REVOKE_SESSION_SCRIPT_SUCCESS_RESULT).equals(result)) {
            throw new IllegalStateException("Session revocation markers were not stored");
        }
    }

    /** 세션 표식의 존재만 확인하고 Redis 오류를 미폐기로 바꾸지 않는다. */
    @Override
    public boolean isSessionRevoked(String sessionId) {
        return commands().exists(sessionKey(sessionId)) > 0;
    }

    /** 사용자 레거시 표식의 존재만 확인해 이전 Access Token을 차단한다. */
    @Override
    public boolean areLegacyUserTokensRevoked(Long userId) {
        return commands().exists(legacyUserKey(userId)) > 0;
    }

    /** 세션 식별자를 Redis 키 뒤에 붙이고 빈 값은 거부한다. */
    private static String sessionKey(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("JWT session ID must not be blank");
        }
        return SESSION_KEY_PREFIX + sessionId;
    }

    /** 양수 사용자 ID로 레거시 토큰 차단 키를 만든다. */
    private static String legacyUserKey(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Legacy token user ID must be positive");
        }
        return LEGACY_USER_KEY_PREFIX + userId;
    }

    /** Redis PX 명령에 안전하게 전달할 양수 밀리초 TTL을 확인한다. */
    private static long positiveTtlMillis(Duration ttl, String name) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.toMillis() <= 0) {
            throw new IllegalArgumentException(name + " revocation TTL must be at least one millisecond");
        }
        return ttl.toMillis();
    }

    /** 실제 Redis 명령이 필요할 때 기존 연결의 동기 명령 인터페이스를 얻는다. */
    private RedisCommands<String, String> commands() {
        return connections.getObject().sync();
    }

    /** 두 차단 키를 함께 기록할 스크립트를 클래스패스에서 한 번 읽는다. */
    private static String loadRevokeSessionScript() {
        try {
            return new ClassPathResource("redis/security/revoke-session.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Session revocation script could not be loaded", exception);
        }
    }
}
