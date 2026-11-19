package com.ktb4.team16.mulo.dailysong.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb4.team16.mulo.dailysong.config.DailySongJobCreationSchedulerConfiguration;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobCreationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class DailySongJobCreationSchedulerTest {
    private final DailySongJobCreationService creations = mock(DailySongJobCreationService.class);

    @Test
    void scheduleUsesSeoulZoneAndDedicatedScheduler() throws Exception {
        Scheduled main = DailySongJobCreationScheduler.class.getMethod("createDailyJobs")
                .getAnnotation(Scheduled.class);
        Scheduled recovery = DailySongJobCreationScheduler.class.getMethod("recoverDailyJobs")
                .getAnnotation(Scheduled.class);
        assertThat(main.cron()).isEqualTo("0 30 0 * * *");
        assertThat(recovery.cron()).isEqualTo("0 * 0-6 * * *");
        assertThat(main.zone()).isEqualTo("Asia/Seoul");
        assertThat(recovery.zone()).isEqualTo("Asia/Seoul");
        assertThat(main.scheduler()).isEqualTo("dailySongJobCreationTaskScheduler");
        assertThat(recovery.scheduler()).isEqualTo("dailySongJobCreationTaskScheduler");
    }

    @Test
    void primaryUsesSeoulDateWithUtcClock() {
        scheduler("2026-11-11T15:30:01Z").createDailyJobs();
        verify(creations).createJobs(LocalDate.of(2026, 11, 12));
    }

    @Test
    void recoveryRunsWithinWindowIncludingLastScheduledMinute() {
        scheduler("2026-11-11T15:31:00Z").recoverDailyJobs();
        scheduler("2026-11-11T21:30:20Z").recoverDailyJobs();
        verify(creations, times(2)).createJobs(LocalDate.of(2026, 11, 12));
    }

    @Test
    void recoveryDoesNotRunOutsideWindowOrDuplicatePrimaryMinute() {
        scheduler("2026-11-11T15:29:59Z").recoverDailyJobs();
        scheduler("2026-11-11T15:30:10Z").recoverDailyJobs();
        scheduler("2026-11-11T21:31:00Z").recoverDailyJobs();
        verifyNoInteractions(creations);
    }

    @Test
    void exceptionDoesNotEscapeScheduler() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        doThrow(new IllegalStateException("테스트 오류")).when(creations).createJobs(date);
        scheduler("2026-11-11T15:30:00Z").createDailyJobs();
        verify(creations).createJobs(date);
    }

    @Test
    void schedulerAndItsThreadPoolAreDisabledByDefault() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DailySongJobCreationScheduler.class);
            assertThat(context).doesNotHaveBean("dailySongJobCreationTaskScheduler");
        });
    }

    @Test
    void schedulerAndItsThreadPoolAreRegisteredWhenEnabled() {
        contextRunner().withPropertyValues("mulo.daily-song.scheduler.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DailySongJobCreationScheduler.class);
            assertThat(context).hasBean("dailySongJobCreationTaskScheduler");
        });
    }

    private DailySongJobCreationScheduler scheduler(String instant) {
        return new DailySongJobCreationScheduler(creations, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withBean(DailySongJobCreationService.class, () -> creations)
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(DailySongJobCreationScheduler.class,
                        DailySongJobCreationSchedulerConfiguration.class);
    }
}
