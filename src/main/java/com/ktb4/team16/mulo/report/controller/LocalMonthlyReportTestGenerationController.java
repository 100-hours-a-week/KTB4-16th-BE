package com.ktb4.team16.mulo.report.controller;

import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportGenerationRequestException;
import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로컬에서 진행 중인 월의 리포트 생성을 검증하기 위한 전용 진입점이다. */
@Profile("local")
@RestController
@RequestMapping("/api/internal/test/monthly-reports")
@RequiredArgsConstructor
public class LocalMonthlyReportTestGenerationController {
    private final MonthlyReportGenerationService generationService;
    private final Clock clock;

    // 테스트 대상 월을 받아 실제 생성 서비스를 호출하고 집계 결과를 반환한다.
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public LocalMonthlyReportGenerationController.LocalMonthlyReportGenerationResponse generate(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        YearMonth targetMonth = resolveTestTargetMonth(year, month);
        var result = generationService.generate(targetMonth);
        return new LocalMonthlyReportGenerationController.LocalMonthlyReportGenerationResponse(
                result.targetMonth().getYear(), result.targetMonth().getMonthValue(),
                result.createdCount(), result.skippedCount());
    }

    // 현재 월까지 허용하되, 유효하지 않거나 미래인 월의 테스트 생성을 차단한다.
    private YearMonth resolveTestTargetMonth(Integer year, Integer month) {
        try {
            YearMonth target = YearMonth.of(year, month);
            if (target.isAfter(YearMonth.now(clock))) {
                throw new InvalidMonthlyReportGenerationRequestException();
            }
            return target;
        } catch (DateTimeException | NullPointerException exception) {
            throw new InvalidMonthlyReportGenerationRequestException();
        }
    }
}
