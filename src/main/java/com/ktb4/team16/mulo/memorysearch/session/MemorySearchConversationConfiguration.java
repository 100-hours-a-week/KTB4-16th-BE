package com.ktb4.team16.mulo.memorysearch.session;

import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.json.JsonMapper;

/** 대화 문맥의 저장 방식과 운영 환경 설정을 선택하고 인기곡 캐시와 분리한다. */
@Configuration(proxyBeanMethods = false)
public class MemorySearchConversationConfiguration {

    /** 지원하지 않는 저장 모드는 시작 시 거절해 인메모리로 조용히 전환되지 않게 한다. */
    public MemorySearchConversationConfiguration(
            @Value("${memory-search.conversation-store:in-memory}") String storeMode, Environment environment) {
        if (!"in-memory".equals(storeMode) && !"redis".equals(storeMode)) {
            throw new IllegalArgumentException("Conversation store must be in-memory or redis");
        }
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        String executionEnvironment = environment.getProperty("AWS_EXECUTION_ENV", "");
        if (executionEnvironment.startsWith("AWS_ECS") && !production) {
            throw new IllegalArgumentException("ECS requires the prod Spring profile for shared conversation state");
        }
        if (production) {
            validateProductionSettings(storeMode, environment);
        }
    }

    /** 운영에서 인메모리 선택과 로컬 기본 주소·환경으로의 잘못된 기동을 차단한다. */
    private void validateProductionSettings(String storeMode, Environment environment) {
        if (!"redis".equals(storeMode)) {
            throw new IllegalArgumentException("Production requires memory-search.conversation-store=redis");
        }
        String namespace = environment.getProperty("memory-search.conversation-environment", "");
        if (namespace.isBlank() || "local".equalsIgnoreCase(namespace)) {
            throw new IllegalArgumentException("Production requires MEMORY_SEARCH_CONVERSATION_ENVIRONMENT");
        }
        // Redis 키의 형식도 연결 전에 검사하고 실제 설정값은 오류 메시지에 출력하지 않는다.
        new MemorySearchRedisKeys(namespace);
        String host = environment.getProperty("mulo.redis.host", "").strip();
        if (host.isBlank() || "localhost".equalsIgnoreCase(host) || host.startsWith("127.")
                || "::1".equals(host) || "[::1]".equals(host) || "0.0.0.0".equals(host)) {
            throw new IllegalArgumentException("Production requires a shared REDIS_HOST, not a loopback address");
        }
        Integer port = environment.getProperty("mulo.redis.port", Integer.class);
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Production requires REDIS_PORT between 1 and 65535");
        }
    }

    /** Redis 모드에서만 기존 공통 지연 연결을 소비하는 문맥 저장소를 등록한다. */
    @Bean
    @ConditionalOnProperty(prefix = "memory-search", name = "conversation-store", havingValue = "redis")
    MemorySearchConversationStore redisMemorySearchConversationStore(
            ObjectProvider<StatefulRedisConnection<String, String>> connections,
            JsonMapper mapper,
            @Value("${memory-search.conversation-idle-ttl}") Duration idleTtl,
            @Value("${memory-search.conversation-environment:local}") String environment) {
        return new RedisMemorySearchConversationStore(connections::getObject, mapper, idleTtl, environment);
    }

}
