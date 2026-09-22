package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserMusicSearchRateLimiterTest {
    @Test
    void limitsEachUserToEightRequestsInsideThirtySeconds() {
        UserMusicSearchRateLimiter limiter = new UserMusicSearchRateLimiter(
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 8, 15);

        for (int count = 0; count < 8; count++) {
            limiter.acquire(1L);
        }

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(MusicSearchRateLimitedException.class);
        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void evictsUsersThatStayedIdleBeyondTheLongWindow() {
        MutableClock clock = new MutableClock();
        UserMusicSearchRateLimiter limiter = new UserMusicSearchRateLimiter(clock, 8, 15);
        limiter.acquire(1L);
        limiter.acquire(2L);

        clock.advance(Duration.ofSeconds(61));
        limiter.acquire(3L);

        @SuppressWarnings("unchecked")
        java.util.Map<Long, ?> tracked = (java.util.Map<Long, ?>)
                ReflectionTestUtils.getField(limiter, "requestsByUser");
        org.assertj.core.api.Assertions.assertThat(tracked).containsOnlyKeys(3L);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        // 테스트에서 rate limit 시간 창을 직접 전진시킨다.
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        // 현재 테스트 시각의 시간대를 반환한다.
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        // 동일한 테스트 시각을 다른 시간대 Clock으로 표현한다.
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        // RateLimiter가 읽을 현재 테스트 시각을 반환한다.
        public Instant instant() {
            return instant;
        }
    }
}
