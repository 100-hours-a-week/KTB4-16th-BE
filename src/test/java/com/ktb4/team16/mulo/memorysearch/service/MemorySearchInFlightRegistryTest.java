package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MemorySearchInFlightRegistryTest {

    // 비동기 테스트가 무한 대기하지 않도록 정한 최대 대기 시간이다.
    private static final long AWAIT_TIMEOUT_SECONDS = 2;

    /** 같은 사용자의 요청은 하나만 허용하고 다른 사용자는 독립적으로 허용한다. */
    @Test
    void permitsOnlyOneActiveRequestPerUserAndReleasesAfterCompletion() throws Exception {
        MemorySearchInFlightRegistry registry = new MemorySearchInFlightRegistry();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                try (var lease = registry.tryAcquire(35L).orElseThrow()) {
                    acquired.countDown();
                    finish.await();
                }
                return true;
            });
            assertThat(acquired.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.tryAcquire(35L)).isEmpty();

            try (var otherUser = registry.tryAcquire(36L).orElseThrow()) {
                assertThat(otherUser).isNotNull();
            }
            finish.countDown();
            assertThat(first.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            try (var next = registry.tryAcquire(35L).orElseThrow()) {
                assertThat(next).isNotNull();
            }
        } finally {
            finish.countDown();
        }
    }

    /** 이전 점유권을 두 번 닫아도 새 요청의 점유권은 유지한다. */
    @Test
    void closingAnOldLeaseCannotReleaseANewLease() {
        MemorySearchInFlightRegistry registry = new MemorySearchInFlightRegistry();
        var old = registry.tryAcquire(35L).orElseThrow();
        old.close();
        var current = registry.tryAcquire(35L).orElseThrow();
        old.close();

        assertThat(registry.tryAcquire(35L)).isEqualTo(Optional.empty());
        current.close();
        assertThat(registry.tryAcquire(35L)).isPresent();
    }
}
