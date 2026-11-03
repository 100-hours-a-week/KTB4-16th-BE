package com.ktb4.team16.mulo.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NotificationAsyncConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationAsyncConfiguration.class);

    @Test
    void registersBoundedNotificationExecutor() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("notificationOutboxExecutor");
            ThreadPoolTaskExecutor executor = context.getBean("notificationOutboxExecutor",
                    ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("notification-outbox-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(20);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        });
    }

    @Test
    void runsSubmittedTaskOnDedicatedThread() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("notificationOutboxExecutor",
                    ThreadPoolTaskExecutor.class);
            AtomicReference<Thread> workerThread = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);

            executor.execute(() -> {
                workerThread.set(Thread.currentThread());
                completed.countDown();
            });

            assertThat(await(completed)).isTrue();
            assertThat(workerThread.get()).isNotSameAs(Thread.currentThread());
            assertThat(workerThread.get().getName()).startsWith("notification-outbox-");
        });
    }

    @Test
    void rejectsWhenQueueIsFullWithoutRunningOnCallerThread() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("notificationOutboxExecutor",
                    ThreadPoolTaskExecutor.class);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean rejectedTaskRan = new AtomicBoolean();

            executor.execute(() -> {
                started.countDown();
                await(release);
            });
            try {
                assertThat(await(started)).isTrue();
                for (int task = 0; task < 20; task++) {
                    executor.execute(() -> { });
                }

                assertThatThrownBy(() -> executor.execute(() -> rejectedTaskRan.set(true)))
                        .isInstanceOf(TaskRejectedException.class);
                assertThat(rejectedTaskRan).isFalse();
            } finally {
                release.countDown();
            }
        });
    }

    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
