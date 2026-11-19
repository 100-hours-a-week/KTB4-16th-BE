package com.ktb4.team16.mulo.dailysong.service;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobCreationRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobCreationRepository.Claim;
import com.ktb4.team16.mulo.dailysong.repository.DailySongTargetUserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DailySongJobCreationService {
    private static final Logger log = LoggerFactory.getLogger(DailySongJobCreationService.class);
    private static final int PAGE_SIZE = 100;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    private final DailySongTargetUserRepository targets;
    private final DailySongJobCreationRepository creations;

    public DailySongJobCreationService(DailySongTargetUserRepository targets,
            DailySongJobCreationRepository creations) {
        this.targets = targets;
        this.creations = creations;
    }

    public void createJobs(LocalDate recommendationDate) {
        LocalDateTime cutoff = recommendationDate.atTime(0, 30);
        Optional<Claim> claimed = creations.claimRun(recommendationDate, cutoff, LEASE_DURATION);
        if (claimed.isEmpty()) {
            return;
        }

        Claim claim = claimed.orElseThrow();
        long lastUserId = 0;
        long targetCount = 0;
        while (!Thread.currentThread().isInterrupted()) {
            List<Long> userIds = targets.findUserIdsAtCutoff(cutoff, lastUserId, PAGE_SIZE);
            if (userIds.isEmpty()) {
                if (!creations.markReady(claim, targetCount)) {
                    log.warn("오늘의 노래 작업 생성 완료를 기록하지 못했습니다: recommendationDate={}", recommendationDate);
                }
                return;
            }
            creations.writeBatch(claim, userIds, LEASE_DURATION);
            targetCount += userIds.size();
            lastUserId = userIds.getLast();
        }
    }
}
