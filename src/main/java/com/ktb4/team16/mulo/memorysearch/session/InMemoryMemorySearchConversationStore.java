package com.ktb4.team16.mulo.memorysearch.session;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** 한 서버 프로세스에서 사용자·대화별 최근 5턴과 유휴 만료 시각을 관리한다. */
@Repository
public class InMemoryMemorySearchConversationStore implements MemorySearchConversationStore {

    private final ConcurrentHashMap<Key, Session> sessions = new ConcurrentHashMap<>();
    private final Duration idleTtl;
    private final Clock clock;

    /** 설정된 유휴 만료 시간과 시스템 시계를 사용해 저장소를 만든다. */
    @Autowired
    public InMemoryMemorySearchConversationStore(
            @Value("${memory-search.conversation-idle-ttl}") Duration idleTtl) {
        this(idleTtl, Clock.systemUTC());
    }

    /** 테스트에서 시간을 제어할 수 있도록 시계를 주입받는다. */
    InMemoryMemorySearchConversationStore(Duration idleTtl, Clock clock) {
        if (idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("Conversation idle TTL must be positive");
        }
        this.idleTtl = idleTtl;
        this.clock = clock;
    }

    /** 새 대화 ID를 발급하고 만료된 항목을 정리한다. */
    @Override
    public String start(Long userId) {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        String conversationId = UUID.randomUUID().toString();
        sessions.put(new Key(userId, conversationId),
                new Session(List.of(), now.plus(idleTtl)));
        return conversationId;
    }

    /** 인증 사용자에게 속한 대화가 만료되지 않았는지 원자적으로 확인한다. */
    @Override
    public boolean isActive(Long userId, String conversationId) {
        return read(userId, conversationId).isPresent();
    }

    /** 활성 대화의 완료된 최근 턴을 오래된 순서로 읽는다. */
    @Override
    public Optional<List<MemorySearchTurn>> read(Long userId, String conversationId) {
        AtomicReference<List<MemorySearchTurn>> result = new AtomicReference<>();
        Instant now = clock.instant();
        sessions.computeIfPresent(new Key(userId, conversationId), (key, session) -> {
            if (expired(session, now)) {
                return null;
            }
            result.set(session.turns());
            return session;
        });
        return Optional.ofNullable(result.get());
    }

    /** 활성 대화에만 완료된 턴을 추가하고 최근 5턴과 유휴 만료를 갱신한다. */
    @Override
    public boolean appendIfActive(Long userId, String conversationId, MemorySearchTurn turn) {
        AtomicReference<Boolean> appended = new AtomicReference<>(false);
        Instant now = clock.instant();
        sessions.computeIfPresent(new Key(userId, conversationId), (key, session) -> {
            if (expired(session, now)) {
                return null;
            }
            List<MemorySearchTurn> turns = new ArrayList<>(session.turns());
            turns.add(turn);
            if (turns.size() > MemorySearchLimits.MAX_RECENT_TURNS) {
                turns.removeFirst();
            }
            appended.set(true);
            return new Session(List.copyOf(turns), now.plus(idleTtl));
        });
        return appended.get();
    }

    /** 명시적으로 종료한 사용자의 지정 대화만 제거한다. */
    @Override
    public void close(Long userId, String conversationId) {
        sessions.remove(new Key(userId, conversationId));
    }

    /** 저장된 유휴 시각에 도달했는지 판단한다. */
    private boolean expired(Session session, Instant now) {
        return !now.isBefore(session.expiresAt());
    }

    private record Key(Long userId, String conversationId) { }

    private record Session(List<MemorySearchTurn> turns, Instant expiresAt) { }
}
