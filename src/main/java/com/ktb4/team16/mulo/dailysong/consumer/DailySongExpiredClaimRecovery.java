package com.ktb4.team16.mulo.dailysong.consumer;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
public class DailySongExpiredClaimRecovery {
    private static final Logger log = LoggerFactory.getLogger(DailySongExpiredClaimRecovery.class);
    private static final int BATCH_SIZE = 100;

    private final DailySongJobClaimRepository claims;
    private final Clock clock;

    public DailySongExpiredClaimRecovery(DailySongJobClaimRepository claims, Clock clock) {
        this.claims = claims;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, scheduler = "dailySongConsumerRecoveryTaskScheduler")
    public void recoverExpiredClaims() {
        try {
            int recovered = claims.recoverExpiredBatch(LocalDateTime.now(clock), BATCH_SIZE);
            if (recovered > 0) {
                log.warn("만료된 오늘의 노래 작업 {}건을 실패 처리했습니다.", recovered);
            }
        } catch (RuntimeException exception) {
            log.error("만료된 오늘의 노래 작업을 회수하지 못했습니다.", exception);
        }
    }
}
