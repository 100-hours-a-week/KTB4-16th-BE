package com.ktb4.team16.mulo.report.controller;

import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportGenerationRequestException;
import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 배포 내부에서 현재·이전 달 리포트를 수동 생성하는 운영 Controller다. */
@RestController
@ConditionalOnProperty("report.operation.token")
@RequestMapping("/internal/ops/monthly-reports")
@RequiredArgsConstructor
public class ReportOperationGenerationController {
    private final MonthlyReportGenerationService generationService;
    private final Clock clock;

    // 검증된 월의 생성 서비스를 실행하고 생성·건너뜀 결과를 반환한다.
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public GenerationResponse generate(@RequestParam Integer year, @RequestParam Integer month) {
        YearMonth targetMonth = resolveAllowedMonth(year, month);
        var result = generationService.generate(targetMonth);
        return new GenerationResponse(result.targetMonth().getYear(), result.targetMonth().getMonthValue(),
                result.createdCount(), result.skippedCount());
    }

    // 현재 월과 이전 월만 수동 실행하도록 제한해 과거 스냅샷의 임의 생성을 막는다.
    private YearMonth resolveAllowedMonth(Integer year, Integer month) {
        try {
            YearMonth target = YearMonth.of(year, month);
            YearMonth current = YearMonth.now(clock);
            if (target.isAfter(current) || target.isBefore(current.minusMonths(1))) {
                throw new InvalidMonthlyReportGenerationRequestException();
            }
            return target;
        } catch (DateTimeException | NullPointerException exception) {
            throw new InvalidMonthlyReportGenerationRequestException();
        }
    }

    public record GenerationResponse(int year, int month, int createdCount, int skippedCount) { }
}
