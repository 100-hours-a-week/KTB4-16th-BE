package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.dto.response.MonthlyReportDetailResponse;
import com.ktb4.team16.mulo.report.dto.response.MonthlyReportListResponse;
import com.ktb4.team16.mulo.report.exception.MonthlyReportNotFoundException;
import com.ktb4.team16.mulo.report.repository.MonthlyMoodStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyPhotoSceneStatRepository;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public List<MonthlyReportListResponse.Report> getReports(Long userId) {
        return reportRepository.findByUser_UserIdOrderByReportYearDescReportMonthDesc(userId).stream()
                .map(MonthlyReportListResponse.Report::from).toList();
    }

    // 리포트 소유권을 조회 조건에 포함해 다른 사용자의 존재 여부를 노출하지 않는다.
    public MonthlyReportDetailResponse.Data getDetail(Long userId, Long monthlyReportId) {
        MonthlyReport report = reportRepository
                .findByMonthlyReportIdAndUser_UserIdWithTopPlace(monthlyReportId, userId)
                .orElseThrow(MonthlyReportNotFoundException::new);
        // 서비스 트랜잭션 안에서 응답 값을 완성해 컨트롤러가 엔티티를 읽지 않게 한다.
        return MonthlyReportDetailResponse.Data.from(report,
                moodStatRepository.findByMonthlyReport_MonthlyReportId(monthlyReportId).orElse(null),
                photoSceneStatRepository.findByMonthlyReport_MonthlyReportIdOrderByCountDesc(monthlyReportId));
    }
}
