package com.ktb4.team16.mulo.memorysearch.session;

import java.util.Objects;

/** 대화 문맥을 환경·사용자별 Redis 영역에 보관하도록 키를 구성한다. */
public final class MemorySearchRedisKeys {
    private final String environment;

    /** 환경 구분에 Redis 해시 태그나 구분자를 삽입하지 못하게 검증한다. */
    public MemorySearchRedisKeys(String environment) {
        if (environment == null || !environment.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Conversation environment must be a safe key segment");
        }
        this.environment = environment;
    }

    /** 지정한 사용자에게 속한 대화 키를 생성한다. */
    public String conversation(Long userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID must not be blank");
        }
        return prefix(userId) + ":conversation:" + conversationId;
    }

    /** 사용자의 대화 키들을 Redis Cluster에서 같은 슬롯에 둔다. */
    private String prefix(Long userId) {
        return "mulo:memory-search:{" + environment + ":u:" + Objects.requireNonNull(userId) + "}";
    }
}
