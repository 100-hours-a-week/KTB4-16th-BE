package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.client.MonthlyReportAiException;
import com.ktb4.team16.mulo.report.batch.MonthlyReportBatchStore;
import com.ktb4.team16.mulo.report.batch.ReportCallbackFingerprint;
import com.ktb4.team16.mulo.report.batch.ReportBatchException;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MonthlyReportBatchStore batchStore;

    /** 기존 단위 테스트와 일지 도입 전 콜백 검증을 위한 호환 생성자다. */
    public MonthlyReportAiCallbackTransactionalService(MonthlyReportRepository reports,
            MonthlyPhotoSceneStatRepository scenes,
            NotificationOutboxEventPublisher notificationEvents) {
        this(reports, scenes, notificationEvents, null);
    }

    /** 콜백 결과와 신규 전송 일지를 같은 업무 트랜잭션에 연결한다. */
    @Autowired
    public MonthlyReportAiCallbackTransactionalService(MonthlyReportRepository reports,
            MonthlyPhotoSceneStatRepository scenes, NotificationOutboxEventPublisher notificationEvents,
            MonthlyReportBatchStore batchStore) {
        this.batchStore = batchStore;
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
                Set<String> tags = new HashSet<>();
                for (var scene : result.photoScenes()) {
                    if (!tags.add(scene.tag())) {
                        throw new InvalidMonthlyReportAiCallbackException();
                    }
                }
                // DB 컬럼의 최대 길이에 맞춰 300자를 초과하는 회고는 저장 전에 거부한다.
                if (result.aiRecap().text().length() > 300) {
                    throw new MonthlyReportAiException();
                }
            } else if (result.errorCode() == null || result.errorCode().isBlank()) {
                throw new InvalidMonthlyReportAiCallbackException();
            }
        }
        for (var result : callback.results()) {
            var report = found.get(result.userId());
            if (batchStore != null && !batchStore.recordCallback(callback.batchRequestId(), result.userId(),
                    result.status().name(), ReportCallbackFingerprint.of(result))) {
                continue;
            }
            if (report.getAiRecapStatus() == com.ktb4.team16.mulo.report.entity.MonthlyReport.AiRecapStatus.COMPLETED) {
                if (result.status() == MonthlyReportAiCallbackRequest.Status.FAILED) {
                    continue;
                }
                var stored = new MonthlyReportAiCallbackRequest.Result(result.userId(), result.status(),
                        new MonthlyReportAiCallbackRequest.AiRecap(report.getAiRecapText()),
                        scenes.findByMonthlyReport_MonthlyReportIdOrderByCountDesc(report.getMonthlyReportId()).stream()
                                .map(scene -> new MonthlyReportAiCallbackRequest.PhotoScene(scene.getSceneTag(),
                                        scene.getCount(), scene.getRatio())).toList(), null);
                if (!ReportCallbackFingerprint.of(stored).equals(ReportCallbackFingerprint.of(result))) {
                    throw new ReportBatchException("REPORT_CALLBACK_CONFLICT");
                }
                continue;
            }
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
