package com.ktb4.team16.mulo.report.controller;

import com.ktb4.team16.mulo.report.dto.response.MonthlyReportDetailResponse;
import com.ktb4.team16.mulo.report.dto.response.MonthlyReportListResponse;
import com.ktb4.team16.mulo.report.service.MonthlyReportQueryService;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monthly-reports")
@RequiredArgsConstructor
@Validated
public class MonthlyReportController {
    private final MonthlyReportQueryService queryService;

    // 인증 사용자의 저장된 월간 리포트 목록을 최신 연·월순으로 반환한다.
    @GetMapping
    public MonthlyReportListResponse getReports(@AuthenticationPrincipal Long userId) {
        List<MonthlyReportListResponse.Report> reports = queryService.getReports(userId).stream()
                .map(MonthlyReportListResponse.Report::from).toList();
        return new MonthlyReportListResponse("월간 리포트 목록 조회 성공",
                new MonthlyReportListResponse.Data(reports));
    }

    // 인증 사용자가 소유한 월간 리포트의 저장된 스냅샷만 상세 반환한다.
    @GetMapping("/{monthlyReportId}")
    public MonthlyReportDetailResponse getDetail(@AuthenticationPrincipal Long userId,
            @PathVariable @Min(1) Long monthlyReportId) {
        return new MonthlyReportDetailResponse("월간 리포트 상세 조회 성공",
                MonthlyReportDetailResponse.Data.from(queryService.getDetail(userId, monthlyReportId)));
    }
}
