package com.ktb4.team16.mulo.music.service;

import com.ktb4.team16.mulo.music.config.MusicSearchRateLimitProperties;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpotifySearchRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(SpotifySearchRateLimiter.class);
    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final long KEY_TTL_MILLIS = WINDOW.plusSeconds(1).toMillis();
    private static final String ACQUIRE_SCRIPT = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000000 + tonumber(time[2])
            local blockedUntil = tonumber(redis.call('GET', KEYS[2]) or '0')
            if blockedUntil > now then
                return {0, blockedUntil - now}
            end
            local nowScore = string.format('%.0f', now)
            local cutoff = string.format('%.0f', now - 30000000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff)
            local requestCount = redis.call('ZCARD', KEYS[1])
            if requestCount >= tonumber(ARGV[2]) then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                return {0, tonumber(oldest[2]) + 30000000 - now}
            end
            redis.call('ZADD', KEYS[1], nowScore, ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return {1, 0}
            """;
    private static final String BLOCK_SCRIPT = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000000 + tonumber(time[2])
            local requestedMicros = tonumber(ARGV[1]) * 1000000
                    + math.floor(tonumber(ARGV[2]) / 1000)
            local candidate = now + requestedMicros
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local blockedUntil = math.max(current, candidate)
            if blockedUntil > now then
                local ttlMillis = math.ceil((blockedUntil - now) / 1000) + 1000
                redis.call('SET', KEYS[1], string.format('%.0f', blockedUntil), 'PX', ttlMillis)
            end
            return 1
            """;

    private final int limit;
    private final ObjectProvider<StatefulRedisConnection<String, String>> redisConnections;
    private final String namespace;

    // 환경 설정의 앱 전체 호출 한도로 운영 RateLimiter를 생성한다.
    // Redis 연결 객체는 실제 검색 요청 시점에 가져온다.
    @Autowired
    public SpotifySearchRateLimiter(SpotifyProperties properties,
            MusicSearchRateLimitProperties rateLimitProperties,
            ObjectProvider<StatefulRedisConnection<String, String>> redisConnections) {
        this.limit = properties.globalLimit30Seconds();
        this.redisConnections = redisConnections;
        this.namespace = rateLimitProperties.namespace();
    }

    // Spotify 30초 호출 창과 현재 429 차단 시간을 검사한다.
    public void acquire() {
        acquireFromRedis();
    }

    // Spotify Retry-After가 끝날 때까지 모든 검색 호출을 차단한다.
    public void block(Duration retryAfter) {
        blockInRedis(retryAfter);
    }

    private void acquireFromRedis() {
        try {
            List<?> result = redisConnections.getObject().sync().eval(ACQUIRE_SCRIPT,
                    ScriptOutputType.MULTI, new String[] {requestsKey(), blockedUntilKey()},
                    UUID.randomUUID().toString(), Integer.toString(limit),
                    Long.toString(KEY_TTL_MILLIS));
            long allowed = responseValue(result, 0);
            if (allowed == 0) {
                throw new MusicSearchRateLimitedException(
                        Duration.ofNanos(Math.max(1, responseValue(result, 1)) * 1_000));
            }
            if (allowed != 1) {
                throw new IllegalStateException("Redis 요청 제한 처리 중 예상하지 못한 응답 수신");
            }
        } catch (BeansException | RedisException | IllegalStateException exception) {
            log.warn("Spotify 전역 요청 제한 처리 중 Redis 오류 발생", exception);
            throw new MusicProviderUnavailableException(exception);
        }
    }

    private void blockInRedis(Duration retryAfter) {
        if (retryAfter.isZero() || retryAfter.isNegative()) {
            return;
        }
        try {
            redisConnections.getObject().sync().eval(BLOCK_SCRIPT, ScriptOutputType.INTEGER,
                    new String[] {blockedUntilKey()}, Long.toString(retryAfter.getSeconds()),
                    Integer.toString(retryAfter.getNano()));
        } catch (BeansException | RedisException | IllegalStateException exception) {
            log.warn("Spotify 전역 요청 제한 처리 중 Redis 오류 발생", exception);
            throw new MusicProviderUnavailableException(exception);
        }
    }

    private String requestsKey() {
        return namespace + ":spotify:global:requests";
    }

    private String blockedUntilKey() {
        return namespace + ":spotify:global:blocked-until";
    }

    private long responseValue(List<?> result, int index) {
        if (result == null || result.size() <= index || !(result.get(index) instanceof Number value)) {
            throw new IllegalStateException("Redis 요청 제한 처리 중 예상하지 못한 응답을 받았습니다.");
        }
        return value.longValue();
    }
}
