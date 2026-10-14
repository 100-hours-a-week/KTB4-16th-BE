package com.ktb4.team16.mulo.match.scheduler;

import com.ktb4.team16.mulo.match.service.MatchRequestExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchRequestExpirationScheduler {

    private final MatchRequestExpirationService expirationService;

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Seoul")
    public void expireTodayWaitingRequests() {
        expirationService.expireTodayWaitingRequests();
    }
}
