package com.ktb4.team16.mulo.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class, ScheduledProbeConfiguration.class);

    @Test
    void 속성이_없으면_예약_작업이_등록된다() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)
                    .getScheduledTasks()).isNotEmpty();
        });
    }

    @Test
    void 켜짐으로_두면_예약_작업이_등록된다() {
        runner.withPropertyValues("mulo.scheduling.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void 끄면_예약_작업이_등록되지_않고_빈은_그대로_만들어진다() {
        runner.withPropertyValues("mulo.scheduling.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context).hasSingleBean(ScheduledProbe.class);
        });
    }

    static class ScheduledProbe {
        final AtomicInteger runs = new AtomicInteger();

        @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
        void tick() {
            runs.incrementAndGet();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ScheduledProbeConfiguration {
        @Bean
        ScheduledProbe scheduledProbe() {
            return new ScheduledProbe();
        }
    }
}
