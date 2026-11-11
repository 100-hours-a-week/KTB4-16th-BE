package com.ktb4.team16.mulo.dailysong.service;

import com.ktb4.team16.mulo.dailysong.dto.DailySongDetailResponse;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobRepository;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailySongQueryService {
    private final DailySongJobRepository jobs;

    public DailySongQueryService(DailySongJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public DailySongDetailResponse getDailySong(Long userId, Long jobId) {
        var job = jobs.findByJobIdAndUserIdAndStatus(jobId, userId, DailySongJobStatus.COMPLETED)
                .orElseThrow(() -> new NotificationDomainException(ErrorCode.AI_RECOMMENDATION_NOT_FOUND));
        return DailySongDetailResponse.from(job);
    }
}
