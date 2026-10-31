package com.ktb4.team16.mulo.music.service;

import com.ktb4.team16.mulo.music.config.MusicSearchRateLimitProperties;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class UserMusicSearchRateLimiter {
    private static final Duration SHORT_WINDOW = Duration.ofSeconds(30);
    private static final Duration LONG_WINDOW = Duration.ofMinutes(1);
    private static final long KEY_TTL_MILLIS = LONG_WINDOW.plusSeconds(1).toMillis();
    private static final String ACQUIRE_SCRIPT = """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000000 + tonumber(time[2])
            local nowScore = string.format('%.0f', now)
            local longCutoff = string.format('%.0f', now - 60000000)
            local shortCutoff = string.format('%.0f', now - 30000000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', longCutoff)
            local shortCount = redis.call('ZCOUNT', KEYS[1], '(' .. shortCutoff, '+inf')
            local longCount = redis.call('ZCARD', KEYS[1])
            local retryAfter = 0
            if shortCount >= tonumber(ARGV[2]) then
                local oldestShort = redis.call('ZRANGEBYSCORE', KEYS[1], '(' .. shortCutoff,
                        '+inf', 'WITHSCORES', 'LIMIT', 0, 1)
                retryAfter = math.max(retryAfter, tonumber(oldestShort[2]) + 30000000 - now)
            end
            if longCount >= tonumber(ARGV[3]) then
                local oldestLong = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                retryAfter = math.max(retryAfter, tonumber(oldestLong[2]) + 60000000 - now)
            end
            if retryAfter > 0 then
                return {0, retryAfter}
            end
            redis.call('ZADD', KEYS[1], nowScore, ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return {1, 0}
            """;

    private final Clock clock;
    private final int shortLimit;
    private final int longLimit;
    private final ObjectProvider<StatefulRedisConnection<String, String>> redisConnections;
    private final String namespace;
    private final Map<Long, ArrayDeque<Instant>> requestsByUser = new HashMap<>();
    private Instant nextCleanupAt = Instant.EPOCH;

    // 환경 설정의 사용자별 한도로 운영 RateLimiter를 생성한다.
    @Autowired
    public UserMusicSearchRateLimiter(Clock clock, SpotifyProperties properties,
            MusicSearchRateLimitProperties rateLimitProperties,
            ObjectProvider<StatefulRedisConnection<String, String>> redisConnections) {
        this.clock = clock;
        this.shortLimit = properties.userLimit30Seconds();
        this.longLimit = properties.userLimit60Seconds();
        this.redisConnections = redisConnections;
        this.namespace = rateLimitProperties.namespace();
    }

    // 테스트가 고정 시간과 한도값을 주입할 수 있게 한다.
    UserMusicSearchRateLimiter(Clock clock, int shortLimit, int longLimit) {
        this.clock = clock;
        this.shortLimit = shortLimit;
        this.longLimit = longLimit;
        this.redisConnections = null;
        this.namespace = null;
    }

    // 사용자의 30초·1분 창을 동시에 검사하고 허용된 호출을 기록한다.
    public void acquire(Long userId) {
        if (redisConnections != null) {
            acquireFromRedis(userId);
            return;
        }
        acquireFromMemory(userId);
    }

    private synchronized void acquireFromMemory(Long userId) {
        Instant now = clock.instant();
        evictIdleUsers(now);
        ArrayDeque<Instant> requests = requestsByUser.computeIfAbsent(
                userId, ignored -> new ArrayDeque<>());
        removeExpired(requests, now.minus(LONG_WINDOW));

        long recentCount = requests.stream()
                .filter(requestAt -> requestAt.isAfter(now.minus(SHORT_WINDOW)))
                .count();
        Duration retryAfter = Duration.ZERO;
        if (recentCount >= shortLimit) {
            Instant oldestRecent = requests.stream()
                    .filter(requestAt -> requestAt.isAfter(now.minus(SHORT_WINDOW)))
                    .findFirst()
                    .orElse(now);
            retryAfter = Duration.between(now, oldestRecent.plus(SHORT_WINDOW));
        }
        if (requests.size() >= longLimit) {
            Duration longRetry = Duration.between(now,
                    requests.getFirst().plus(LONG_WINDOW));
            if (longRetry.compareTo(retryAfter) > 0) {
                retryAfter = longRetry;
            }
        }
        if (!retryAfter.isZero() && !retryAfter.isNegative()) {
            throw new MusicSearchRateLimitedException(retryAfter);
        }
        requests.addLast(now);
    }

    private void acquireFromRedis(Long userId) {
        try {
            StatefulRedisConnection<String, String> redisConnection = redisConnections.getObject();
            List<?> result = redisConnection.sync().eval(ACQUIRE_SCRIPT, ScriptOutputType.MULTI,
                    new String[] {key(userId)}, UUID.randomUUID().toString(),
                    Integer.toString(shortLimit), Integer.toString(longLimit),
                    Long.toString(KEY_TTL_MILLIS));
            long allowed = responseValue(result, 0);
            if (allowed == 0) {
                throw new MusicSearchRateLimitedException(
                        Duration.ofNanos(responseValue(result, 1) * 1_000));
            }
            if (allowed != 1) {
                throw new IllegalStateException("Unexpected Redis rate limit response");
            }
        } catch (BeansException | RedisException | IllegalStateException exception) {
            throw new MusicProviderUnavailableException(exception);
        }
    }

    private String key(Long userId) {
        return namespace + ":user:" + userId;
    }

    private long responseValue(List<?> result, int index) {
        if (result == null || result.size() <= index || !(result.get(index) instanceof Number value)) {
            throw new IllegalStateException("Unexpected Redis rate limit response");
        }
        return value.longValue();
    }

    // 1분마다 모든 사용자 기록을 훑어 긴 창 밖의 빈 사용자 항목을 제거한다.
    private void evictIdleUsers(Instant now) {
        if (now.isBefore(nextCleanupAt)) {
            return;
        }
        Instant cutoff = now.minus(LONG_WINDOW);
        requestsByUser.entrySet().removeIf(entry -> {
            removeExpired(entry.getValue(), cutoff);
            return entry.getValue().isEmpty();
        });
        nextCleanupAt = now.plus(LONG_WINDOW);
    }

    // 1분 창 밖으로 벗어난 호출 기록을 제거한다.
    private void removeExpired(ArrayDeque<Instant> requests, Instant cutoff) {
        while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff)) {
            requests.removeFirst();
        }
    }
}
