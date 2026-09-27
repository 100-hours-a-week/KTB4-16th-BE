package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.client.MonthlyReportAiException;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.entity.MonthlyPhotoSceneStat;
import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportAiCallbackException;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportAiCallbackService {
    private final MonthlyReportRepository reports;
    private final MonthlyPhotoSceneStatRepository scenes;
    public MonthlyReportAiCallbackService(MonthlyReportRepository reports,
            MonthlyPhotoSceneStatRepository scenes) { this.reports = reports; this.scenes = scenes; }
    // 전체 콜백을 검증한 뒤 AI 소유 회고와 사진 장면 통계를 한 트랜잭션으로 교체한다.
    @Transactional public void apply(MonthlyReportAiCallbackRequest callback) {
        Map<Long, com.ktb4.team16.mulo.report.entity.MonthlyReport> found = reports
                .findByReportYearAndReportMonth((short) callback.year(), (short) callback.month()).stream()
                .collect(java.util.stream.Collectors.toMap(report -> report.getUser().getUserId(), Function.identity()));
        Set<Long> ids = new HashSet<>();
        for (var result : callback.results()) {
            if (!ids.add(result.userId()) || !found.containsKey(result.userId())) {
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
            } else {
                report.markFailedUnlessCompleted();
            }
        }
    }
}
