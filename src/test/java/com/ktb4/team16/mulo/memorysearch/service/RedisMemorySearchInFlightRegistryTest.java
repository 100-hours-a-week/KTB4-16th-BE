package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.memorysearch.session.MemorySearchConversationStore;
import com.ktb4.team16.mulo.memorysearch.session.MemorySearchTurn;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisMemorySearchInFlightRegistryTest {
    private static final String KEY = "mulo:memory-search:{test:u:35}:inflight";
    @Mock
    private StatefulRedisConnection<String, String> connection;
    @Mock
    private RedisCommands<String, String> commands;
    private RedisMemorySearchInFlightRegistry registry;

    /** 실제 네트워크 없이 NX 응답과 통신 오류를 제어한다. */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(connection.sync()).thenReturn(commands);
        registry = new RedisMemorySearchInFlightRegistry(() -> connection, Duration.ofSeconds(60), "test");
    }

    /** 이미 키가 있으면 점유권을 발급하지 않는다. */
    @Test
    void busyUserDoesNotReceiveLease() {
        when(commands.set(eq(KEY), anyString(), any(SetArgs.class))).thenReturn(null);
        assertThat(registry.tryAcquire(35L)).isEmpty();
        verify(commands).set(eq(KEY), anyString(), any(SetArgs.class));
    }

    /** 불명확한 획득 결과는 busy나 성공으로 바꾸거나 자동 재시도하지 않는다. */
    @Test
    void acquisitionTimeoutFailsClosedWithoutRetry() {
        when(commands.set(eq(KEY), anyString(), any(SetArgs.class)))
                .thenThrow(new RedisCommandTimeoutException("timeout"));
        assertThatThrownBy(() -> registry.tryAcquire(35L)).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RedisCommandTimeoutException.class);
        verify(commands).set(eq(KEY), anyString(), any(SetArgs.class));
    }

    /** 토큰 비교 삭제는 한 번만 실행하고 중복 close는 아무것도 해제하지 않는다. */
    @Test
    void closeUsesOwnedReleaseOnlyOnce() {
        when(commands.set(eq(KEY), anyString(), any(SetArgs.class))).thenReturn("OK");
        doReturn(1L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        var lease = registry.tryAcquire(35L).orElseThrow();
        lease.close();
        lease.close();
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        assertThatThrownBy(() -> lease.appendIfActive(org.mockito.Mockito.mock(MemorySearchConversationStore.class),
                "conversation", new MemorySearchTurn("질문", List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 해제만 실패한 경우 TTL에 맡기고 완료된 요청을 실패로 뒤집지 않는다. */
    @Test
    void releaseFailureIsBestEffortWithoutRetry() {
        when(commands.set(eq(KEY), anyString(), any(SetArgs.class))).thenReturn("OK");
        when(commands.eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class)))
                .thenThrow(new RedisCommandTimeoutException("timeout"));
        var lease = registry.tryAcquire(35L).orElseThrow();
        lease.close();
        lease.close();
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
    }

    /** 활성 Lease는 사용자·토큰을 소유권 검증 완료 계약에 전달한다. */
    @Test
    void completionUsesOwnerGuardedStoreContract() {
        when(commands.set(eq(KEY), anyString(), any(SetArgs.class))).thenReturn("OK");
        var store = org.mockito.Mockito.mock(MemorySearchConversationStore.class);
        var turn = new MemorySearchTurn("질문", List.of(9L));
        when(store.appendIfOwned(eq(35L), eq("conversation"), anyString(), eq(turn))).thenReturn(true);
        var lease = registry.tryAcquire(35L).orElseThrow();
        assertThat(lease.appendIfActive(store, "conversation", turn)).isTrue();
        verify(store).appendIfOwned(eq(35L), eq("conversation"), anyString(), eq(turn));
    }

    /** 무기한 또는 밀리초 미만 잠금과 잘못된 환경 키를 허용하지 않는다. */
    @Test
    void validatesLeaseDurationAndEnvironment() {
        assertThatThrownBy(() -> new RedisMemorySearchInFlightRegistry(() -> connection,
                Duration.ZERO, "test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisMemorySearchInFlightRegistry(() -> connection,
                Duration.ofNanos(1), "test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisMemorySearchInFlightRegistry(() -> connection,
                Duration.ofSeconds(60), "bad}:key")).isInstanceOf(IllegalArgumentException.class);
    }
}
