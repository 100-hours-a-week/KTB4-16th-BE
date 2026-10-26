package com.ktb4.team16.mulo.notification.outbox;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 알림 Outbox의 향후 비동기 처리를 위한 전용 실행 환경이다. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class NotificationAsyncConfiguration {

    /** DB 연결 풀과의 경합을 제한하고 큐가 가득 차면 제출을 거절한다. */
    @Bean("notificationOutboxExecutor")
    public ThreadPoolTaskExecutor notificationOutboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("notification-outbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
