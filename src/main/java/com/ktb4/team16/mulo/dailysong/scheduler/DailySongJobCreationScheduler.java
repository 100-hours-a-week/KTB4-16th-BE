package com.ktb4.team16.mulo.dailysong.scheduler;

import com.ktb4.team16.mulo.dailysong.service.DailySongJobCreationService;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mulo.daily-song.scheduler", name = "enabled", havingValue = "true")
public class DailySongJobCreationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailySongJobCreationScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime START = LocalTime.of(0, 30);
    private static final LocalTime RECOVERY_END = LocalTime.of(6, 30);

    private final DailySongJobCreationService creations;
    private final Clock clock;

    public DailySongJobCreationScheduler(DailySongJobCreationService creations, Clock clock) {
        this.creations = creations;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul", scheduler = "dailySongJobCreationTaskScheduler")
    public void createDailyJobs() {
        runIfInWindow(false);
    }

    @Scheduled(cron = "0 * 0-6 * * *", zone = "Asia/Seoul", scheduler = "dailySongJobCreationTaskScheduler")
    public void recoverDailyJobs() {
        runIfInWindow(true);
    }

    private void runIfInWindow(boolean recovery) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(SEOUL));
        LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        if (time.isBefore(START) || time.isAfter(RECOVERY_END)
                || (recovery && time.getHour() == 0 && time.getMinute() == 30)) {
            return;
        }
        try {
            creations.createJobs(now.toLocalDate());
        } catch (RuntimeException exception) {
            log.error("오늘의 노래 작업 생성에 실패했습니다: recommendationDate={}", now.toLocalDate(), exception);
        }
    }
}
