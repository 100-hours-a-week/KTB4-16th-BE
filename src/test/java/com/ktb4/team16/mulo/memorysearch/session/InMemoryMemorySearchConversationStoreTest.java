package com.ktb4.team16.mulo.memorysearch.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryMemorySearchConversationStoreTest {

    private static final Duration IDLE_TTL = Duration.ofMinutes(30);

    /** 사용자·대화별 문맥을 분리하고 완료된 최근 5턴만 오래된 순서로 보존한다. */
    @Test
    void keepsOnlyFiveCompletedTurnsWithinOwnedConversation() {
        var store = new InMemoryMemorySearchConversationStore(IDLE_TTL,
                Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC));
        String first = store.start(35L);
        String second = store.start(35L);

        for (int index = 1; index <= MemorySearchLimits.MAX_RECENT_TURNS + 1; index++) {
            assertThat(store.appendIfActive(35L, first,
                    new MemorySearchTurn("질문 " + index, List.of((long) index)))).isTrue();
        }

        assertThat(store.read(35L, first).orElseThrow())
                .extracting(MemorySearchTurn::query)
                .containsExactly("질문 2", "질문 3", "질문 4", "질문 5", "질문 6");
        assertThat(store.read(35L, second).orElseThrow()).isEmpty();
        assertThat(store.read(36L, first)).isEmpty();
    }

    /** 30분 만료와 명시적 종료 후에는 늦은 턴이 대화를 다시 만들 수 없다. */
    @Test
    void rejectsAppendAfterExpiryOrClose() {
        Instant startedAt = Instant.parse("2026-10-15T00:00:00Z");
        var clock = new MutableClock(startedAt);
        var store = new InMemoryMemorySearchConversationStore(IDLE_TTL, clock);
        String closed = store.start(35L);
        store.close(35L, closed);
        assertThat(store.appendIfActive(35L, closed,
                new MemorySearchTurn("늦은 질문", List.of(1L)))).isFalse();

        String expired = store.start(35L);
        clock.advance(IDLE_TTL);
        assertThat(store.isActive(35L, expired)).isFalse();
        assertThat(store.read(35L, expired)).isEmpty();
        assertThat(store.appendIfActive(35L, expired,
                new MemorySearchTurn("늦은 질문", List.of(1L)))).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        /** 테스트에서 실제 시간을 기다리지 않고 유휴 만료 시점을 이동한다. */
        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        /** 모든 테스트 시각은 UTC를 사용한다. */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** 이 테스트의 시계는 UTC에서만 사용한다. */
        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("UTC only");
            }
            return this;
        }

        /** 현재 가상 시각을 반환한다. */
        @Override
        public Instant instant() {
            return now;
        }
    }
}
