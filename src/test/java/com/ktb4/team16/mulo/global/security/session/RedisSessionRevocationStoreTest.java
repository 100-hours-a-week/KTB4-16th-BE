package com.ktb4.team16.mulo.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class RedisSessionRevocationStoreTest {
    private static final String SESSION_ID = "test-session-id";
    private static final Long USER_ID = 42L;
    private static final String SESSION_KEY = "mulo:security:revoked-session:" + SESSION_ID;
    private static final String LEGACY_USER_KEY = "mulo:security:legacy-user-revoked:" + USER_ID;

    @Mock
    private ObjectProvider<StatefulRedisConnection<String, String>> connections;

    @Mock
    private StatefulRedisConnection<String, String> connection;

    @Mock
    private RedisCommands<String, String> commands;

    private RedisSessionRevocationStore store;

    /** 테스트 저장소가 실제 Redis 대신 제어 가능한 Lettuce 명령 객체를 사용한다. */
    @BeforeEach
    void setUp() {
        lenient().when(connections.getObject()).thenReturn(connection);
        lenient().when(connection.sync()).thenReturn(commands);
        store = new RedisSessionRevocationStore(connections);
    }

    /** Lua 스크립트가 세션 키와 레거시 사용자 키에 각 TTL을 원자적으로 저장하는지 확인한다. */
    @Test
    void revokeSessionWritesBothKeysAtomically() {
        doReturn(1L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));

        store.revokeSession(SESSION_ID, Duration.ofDays(7), USER_ID, Duration.ofHours(1));

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> keys = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<String[]> args = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(script.capture(), eq(ScriptOutputType.INTEGER), keys.capture(), args.capture());
        assertThat(script.getValue()).contains("PSETEX");
        assertThat(keys.getValue()).containsExactly(SESSION_KEY, LEGACY_USER_KEY);
        assertThat(args.getValue()).containsExactly("604800000", "3600000");
    }

    /** Redis 세션 키 존재 여부를 폐기 상태로 반환하는지 확인한다. */
    @Test
    void isSessionRevokedReflectsRedisExistence() {
        when(commands.exists(SESSION_KEY)).thenReturn(1L, 0L);

        assertThat(store.isSessionRevoked(SESSION_ID)).isTrue();
        assertThat(store.isSessionRevoked(SESSION_ID)).isFalse();
    }

    /** Redis 레거시 사용자 키 존재 여부를 폐기 상태로 반환하는지 확인한다. */
    @Test
    void legacyUserRevocationReflectsRedisExistence() {
        when(commands.exists(LEGACY_USER_KEY)).thenReturn(1L, 0L);

        assertThat(store.areLegacyUserTokensRevoked(USER_ID)).isTrue();
        assertThat(store.areLegacyUserTokensRevoked(USER_ID)).isFalse();
    }

    /** Redis 명령 제한시간 예외가 호출자에게 그대로 전달되는지 확인한다. */
    @Test
    void redisTimeoutIsPropagated() {
        RedisCommandTimeoutException timeout = new RedisCommandTimeoutException("offline");
        when(commands.exists(SESSION_KEY)).thenThrow(timeout);

        assertThatThrownBy(() -> store.isSessionRevoked(SESSION_ID)).isSameAs(timeout);
    }

    /** 0 또는 밀리초로 환산할 수 없는 TTL을 저장 전에 거부하는지 확인한다. */
    @Test
    void revokeSessionRejectsNonPositiveTtls() {
        assertThatThrownBy(() -> store.revokeSession(SESSION_ID, Duration.ZERO, USER_ID, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.revokeSession(SESSION_ID, Duration.ofSeconds(1), USER_ID, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
