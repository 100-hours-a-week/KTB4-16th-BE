package com.ktb4.team16.mulo.dailysong.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mulo.daily-song.scheduler", name = "enabled", havingValue = "true")
public class DailySongNotificationSchedulerConfiguration {
    @Bean
    TaskScheduler dailySongNotificationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("daily-song-notification-");
        return scheduler;
    }
}
