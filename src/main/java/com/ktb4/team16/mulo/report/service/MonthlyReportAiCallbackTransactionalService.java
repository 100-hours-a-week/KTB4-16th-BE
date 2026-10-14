package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.client.MonthlyReportAiException;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.entity.MonthlyPhotoSceneStat;
import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportAiCallbackException;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import com.ktb4.team16.mulo.notification.event.NotificationEventPayloads;
import com.ktb4.team16.mulo.notification.event.NotificationEventType;
import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxEventPublisher;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportAiCallbackTransactionalService {
    private final MonthlyReportRepository reports;
    private final MonthlyPhotoSceneStatRepository scenes;
    private final NotificationOutboxEventPublisher notificationEvents;
    public MonthlyReportAiCallbackTransactionalService(MonthlyReportRepository reports,
            MonthlyPhotoSceneStatRepository scenes,
            NotificationOutboxEventPublisher notificationEvents) {
        this.reports = reports;
        this.scenes = scenes;
        this.notificationEvents = notificationEvents;
    }
    /** 전체 콜백을 검증하고 AI 결과 및 Outbox 이벤트를 한 transaction에 저장한다. */
    @Transactional
    public List<UUID> apply(MonthlyReportAiCallbackRequest callback) {
        List<UUID> eventIds = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (var result : callback.results()) {
            if (result.userId() == null || !ids.add(result.userId())) {
                throw new InvalidMonthlyReportAiCallbackException();
            }
        }
        // 사용자 ID 순서로 행을 잠가 ID 예약과 콜백 저장의 경합을 직렬화한다.
        Map<Long, com.ktb4.team16.mulo.report.entity.MonthlyReport> found = new HashMap<>();
        for (Long userId : ids.stream().sorted().toList()) {
            found.put(userId, reports.findForUpdate(userId, (short) callback.year(),
                    (short) callback.month())
                    .orElseThrow(InvalidMonthlyReportAiCallbackException::new));
        }
        for (var result : callback.results()) {
            // 저장된 시도 ID가 없는 기존 리포트는 기존 콜백만 허용한다.
            if (!Objects.equals(found.get(result.userId()).getBatchRequestId(),
                    callback.batchRequestId())) {
                throw new InvalidMonthlyReportAiCallbackException();
            }
            if (result.status() == MonthlyReportAiCallbackRequest.Status.COMPLETED) {
                if (result.aiRecap() == null || result.photoScenes() == null) {
                    throw new InvalidMonthlyReportAiCallbackException();
                }
                if (result.aiRecap().text().length() > 100) {
                    throw new MonthlyReportAiException();
                }
            } else if (result.errorCode() == null || result.errorCode().isBlank()) {
                throw new InvalidMonthlyReportAiCallbackException();
            }
        }
        for (var result : callback.results()) {
            var report = found.get(result.userId());
            if (result.status() == MonthlyReportAiCallbackRequest.Status.COMPLETED) {
                scenes.deleteByMonthlyReport_MonthlyReportId(report.getMonthlyReportId());
                result.photoScenes().forEach(scene -> scenes.save(MonthlyPhotoSceneStat.create(report,
                        scene.tag(), scene.count(), (byte) scene.ratio())));
                report.complete(result.aiRecap().text());
                eventIds.add(notificationEvents.publish(NotificationEventType.MONTHLY_REPORT_CREATED,
                        report.getMonthlyReportId(), new NotificationEventPayloads.MonthlyReportCreated(
                                1, List.of(report.getUser().getUserId()), report.getMonthlyReportId(),
                                report.getReportYear(), report.getReportMonth())));
            } else {
                report.markFailedUnlessCompleted();
            }
        }
        return List.copyOf(eventIds);
    }
}
