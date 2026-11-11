package com.ktb4.team16.mulo.dailysong.service;

import com.ktb4.team16.mulo.dailysong.repository.DailySongNotificationRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongNotificationRepository.NotificationCandidate;
import com.ktb4.team16.mulo.notification.sse.NotificationCreatedNotifier;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DailySongNotificationBatchService {
    private static final Logger log = LoggerFactory.getLogger(DailySongNotificationBatchService.class);
    public static final int BATCH_SIZE = 100;
    private static final LocalTime RELEASE_TIME = LocalTime.of(7, 0);
    private static final String TITLE = "오늘의 노래";
    private static final String CONTENT = "오늘의 추천곡이 도착했어요.";

    private final DailySongNotificationRepository notifications;
    private final NotificationCreatedNotifier notifier;
    private final Clock clock;

    public DailySongNotificationBatchService(DailySongNotificationRepository notifications,
            NotificationCreatedNotifier notifier, Clock clock) {
        this.notifications = notifications;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public int createNextBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate throughDate = now.toLocalTime().isBefore(RELEASE_TIME)
                ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        var candidates = notifications.lockDueJobs(throughDate, BATCH_SIZE);
        if (candidates.isEmpty()) {
            return 0;
        }

        notifications.insertNotifications(candidates, TITLE, CONTENT);
        int updated = notifications.markNotified(candidates.stream().map(NotificationCandidate::jobId).toList(), now);
        if (updated != candidates.size()) {
            throw new IllegalStateException("오늘의 노래 알림 생성 상태를 저장하지 못했습니다.");
        }

        Set<Long> recipients = candidates.stream().map(NotificationCandidate::userId).collect(Collectors.toSet());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notifier.notificationCreated(recipients);
                } catch (RuntimeException exception) {
                    log.warn("오늘의 노래 알림 SSE 신호를 제출하지 못했습니다: failureType={}",
                            exception.getClass().getSimpleName());
                }
            }
        });
        return candidates.size();
    }
}
