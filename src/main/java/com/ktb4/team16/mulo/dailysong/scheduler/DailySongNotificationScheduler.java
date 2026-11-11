package com.ktb4.team16.mulo.dailysong.scheduler;

import com.ktb4.team16.mulo.dailysong.service.DailySongNotificationBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mulo.daily-song.scheduler", name = "enabled", havingValue = "true")
public class DailySongNotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailySongNotificationScheduler.class);
    private final DailySongNotificationBatchService batches;

    public DailySongNotificationScheduler(DailySongNotificationBatchService batches) {
        this.batches = batches;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul", scheduler = "dailySongNotificationTaskScheduler")
    public void createReadyNotifications() {
        try {
            int created;
            do {
                created = batches.createNextBatch();
            } while (created == DailySongNotificationBatchService.BATCH_SIZE);
        } catch (RuntimeException exception) {
            log.error("오늘의 노래 알림 생성에 실패했습니다.", exception);
        }
    }
}
