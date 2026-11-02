package com.ktb4.team16.mulo.memorysearch.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RedisMemorySearchConversationStoreTest {
    private static final String KEY = "mulo:memory-search:{test:u:35}:conversation:conversation";
    @Mock
    private StatefulRedisConnection<String, String> connection;
    @Mock
    private RedisCommands<String, String> commands;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private RedisMemorySearchConversationStore store;

    /** 단위 테스트는 실제 네트워크 없이 명령의 반환·실패를 제어한다. */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(connection.sync()).thenReturn(commands);
        store = new RedisMemorySearchConversationStore(connection, mapper, Duration.ofMinutes(30), "test");
    }

    /** 생성 성공을 확인한 뒤 사용자 영역의 UUID 대화 ID를 반환한다. */
    @Test
    void startsActiveEmptyConversation() {
        doReturn(1L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        String id = store.start(35L);
        assertThat(id).matches("[a-f0-9-]{36}");
        var keys = ArgumentCaptor.forClass(String[].class);
        var args = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER), keys.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly("mulo:memory-search:{test:u:35}:conversation:" + id);
        assertThat(args.getValue()).contains("1800000", "[]");
    }

    /** 빈 활성 문맥과 존재하지 않는 문맥의 반환값을 구분한다. */
    @Test
    void missingConversationDiffersFromEmptyHistory() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(
                payload("1", "[]"), List.of(KeyValue.empty("schemaVersion"), KeyValue.empty("turns")));
        when(commands.exists(KEY)).thenReturn(0L);
        assertThat(store.read(35L, "conversation")).hasValue(List.of());
        assertThat(store.read(35L, "conversation")).isEmpty();
    }

    /** 한글·따옴표와 Java Long 최댓값을 손실 없이 역직렬화한다. */
    @Test
    void preservesKoreanQuotesAndLongRecordIds() {
        var turns = List.of(new MemorySearchTurn("비 오는 날 \"노래\"\n다시", List.of(Long.MAX_VALUE)));
        when(commands.hmget(KEY, "schemaVersion", "turns"))
                .thenReturn(payload("1", mapper.writeValueAsString(turns)));
        assertThat(store.read(35L, "conversation")).hasValue(turns);
    }

    /** 알 수 없는 형식과 손상된 JSON을 정상 빈 문맥으로 숨기지 않는다. */
    @Test
    void rejectsMalformedOrUnsupportedPayload() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(
                payload("2", "[]"), payload("1", "not-json"), payload("1", "null"),
                payload("1", "[null]"));
        for (int index = 0; index < 4; index++) {
            assertThatThrownBy(() -> store.read(35L, "conversation"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /** 기존 키의 필드 누락은 대화 만료와 구분한다. */
    @Test
    void rejectsMissingFieldsOnExistingKey() {
        when(commands.hmget(KEY, "schemaVersion", "turns"))
                .thenReturn(List.of(KeyValue.empty("schemaVersion"), KeyValue.empty("turns")));
        when(commands.exists(KEY)).thenReturn(1L);
        assertThatThrownBy(() -> store.read(35L, "conversation"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 생성·갱신 키가 무기한 남지 않도록 양의 밀리초 TTL만 허용한다. */
    @Test
    void rejectsNonPositiveTtlAndUnsafeEnvironment() {
        assertThatThrownBy(() -> new RedisMemorySearchConversationStore(connection, mapper,
                Duration.ZERO, "test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisMemorySearchConversationStore(connection, mapper,
                Duration.ofNanos(1), "test")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisMemorySearchConversationStore(connection, mapper,
                Duration.ofMinutes(30), "prod}:other")).isInstanceOf(IllegalArgumentException.class);
    }

    /** 활성 여부 조회는 키의 존재만 확인하며 만료 시간을 연장하지 않는다. */
    @Test
    void checksActivityWithoutWriting() {
        when(commands.exists(KEY)).thenReturn(1L, 0L);
        assertThat(store.isActive(35L, "conversation")).isTrue();
        assertThat(store.isActive(35L, "conversation")).isFalse();
        verify(commands, never()).eval(anyString(), any(), any(String[].class), any(String[].class));
    }

    /** 확인된 값 충돌에만 최신 문맥을 다시 읽고 새 JSON을 계산한다. */
    @Test
    void retriesOnlyConfirmedCasConflict() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(payload("1", "[]"),
                payload("1", "[{\"query\":\"다른 질문\",\"recordIds\":[7]}]"));
        doReturn(2L, 1L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        assertThat(store.appendIfActive(35L, "conversation",
                new MemorySearchTurn("내 질문", List.of(9L)))).isTrue();
        var args = ArgumentCaptor.forClass(String[].class);
        verify(commands, org.mockito.Mockito.times(2)).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), args.capture());
        assertThat(args.getAllValues().get(1)[1]).contains("다른 질문", "내 질문");
    }

    /** 반복 경합을 무한 재시도하거나 없는 대화로 취급하지 않는다. */
    @Test
    void failsAfterBoundedCasConflicts() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(payload("1", "[]"));
        doReturn(2L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        assertThatThrownBy(() -> store.appendIfActive(35L, "conversation",
                new MemorySearchTurn("질문", List.of()))).isInstanceOf(IllegalStateException.class);
        verify(commands, org.mockito.Mockito.times(3)).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
    }

    /** 쓰기 응답이 불명확하면 같은 질문을 자동 재등록하지 않는다. */
    @Test
    void doesNotRetryAmbiguousWriteTimeout() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(payload("1", "[]"));
        when(commands.eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class)))
                .thenThrow(new RedisCommandTimeoutException("timeout"));
        assertThatThrownBy(() -> store.appendIfActive(35L, "conversation",
                new MemorySearchTurn("질문", List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RedisCommandTimeoutException.class);
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
    }

    /** 조회 이후 삭제된 대화는 생성하지 않고 false로 반환한다. */
    @Test
    void rejectsAppendIfClosedAfterRead() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(payload("1", "[]"));
        doReturn(0L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        assertThat(store.appendIfActive(35L, "conversation", new MemorySearchTurn("질문", List.of())))
                .isFalse();
    }

    /** 연결 실패는 정상 만료나 비활성 상태로 위장하지 않는다. */
    @Test
    void propagatesRedisFailureWithoutMemoryFallback() {
        when(commands.exists(KEY)).thenThrow(new RedisCommandTimeoutException("offline"));
        assertThatThrownBy(() -> store.isActive(35L, "conversation"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RedisCommandTimeoutException.class);
    }

    /** 종료는 지정한 사용자·대화 키만 삭제하고 없는 키도 허용한다. */
    @Test
    void closesOnlyOwnedConversationAndAllowsRepeatedClose() {
        when(commands.del(KEY)).thenReturn(1L, 0L);
        store.close(35L, "conversation");
        store.close(35L, "conversation");
        verify(commands, org.mockito.Mockito.times(2)).del(KEY);
    }

    /** 점유권 완료는 문맥과 사용자 잠금 키를 같은 원자적 스크립트에 전달한다. */
    @Test
    void completesWithUserLockAndConversationInSameScript() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(payload("1", "[]"));
        doReturn(1L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        assertThat(store.appendIfOwned(35L, "conversation", "owner-token",
                new MemorySearchTurn("질문", List.of()))).isTrue();
        var keys = ArgumentCaptor.forClass(String[].class);
        var args = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER), keys.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly(KEY, "mulo:memory-search:{test:u:35}:inflight");
        assertThat(args.getValue()[3]).isEqualTo("owner-token");
    }

    /** 문맥이 살아 있어도 만료·교체된 요청의 완료는 실패하고 재시도하지 않는다. */
    @Test
    void expiredOwnerCannotCommitAndDoesNotRetry() {
        when(commands.hmget(KEY, "schemaVersion", "turns")).thenReturn(payload("1", "[]"));
        doReturn(3L).when(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
        assertThatThrownBy(() -> store.appendIfOwned(35L, "conversation", "old-token",
                new MemorySearchTurn("늦은 응답", List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Memory search request lease is no longer valid");
        verify(commands).eval(anyString(), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));
    }

    /** HMGET 응답에서 스키마와 문맥 필드를 순서대로 구성한다. */
    private List<KeyValue<String, String>> payload(String schema, String turns) {
        return List.of(KeyValue.just("schemaVersion", schema), KeyValue.just("turns", turns));
    }
}
