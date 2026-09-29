package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.entity.MonthlyMoodStat;
import com.ktb4.team16.mulo.report.entity.MonthlyPhotoSceneStat;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.exception.MonthlyReportNotFoundException;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyReportQueryService {
    private final MonthlyReportRepository reportRepository;
    private final MonthlyMoodStatRepository moodStatRepository;
    private final MonthlyPhotoSceneStatRepository photoSceneStatRepository;

    // 인증 사용자의 월간 리포트 스냅샷을 최신 연·월 순으로 반환한다.
    public List<MonthlyReport> getReports(Long userId) {
        return reportRepository.findByUser_UserIdOrderByReportYearDescReportMonthDesc(userId);
    }

    // 리포트 소유권을 조회 조건에 포함해 다른 사용자의 존재 여부를 노출하지 않는다.
    public MonthlyReportDetail getDetail(Long userId, Long monthlyReportId) {
        MonthlyReport report = reportRepository.findByMonthlyReportIdAndUser_UserId(monthlyReportId, userId)
                .orElseThrow(MonthlyReportNotFoundException::new);
        // open-in-view가 꺼져있어 컨트롤러(트랜잭션 밖)에서 지연 로딩 필드에 접근하면
        // LazyInitializationException이 나므로, 세션이 살아있는 여기서 미리 초기화해둔다.
        Hibernate.initialize(report.getTopPlace());
        MonthlyMoodStat moodStat = moodStatRepository.findByMonthlyReport_MonthlyReportId(monthlyReportId)
                .orElse(null);
        List<MonthlyPhotoSceneStat> photoScenes = photoSceneStatRepository
                .findByMonthlyReport_MonthlyReportIdOrderByCountDesc(monthlyReportId);
        return new MonthlyReportDetail(report, moodStat, photoScenes);
    }

    public record MonthlyReportDetail(MonthlyReport report, MonthlyMoodStat moodStat,
            List<MonthlyPhotoSceneStat> photoScenes) {
    }
}
