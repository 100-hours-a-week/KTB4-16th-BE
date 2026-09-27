package com.ktb4.team16.mulo.report.controller;

import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService;
import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportGenerationRequestException;
import java.time.DateTimeException;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/internal/monthly-reports")
@RequiredArgsConstructor
public class LocalMonthlyReportGenerationController {
    private final MonthlyReportGenerationService generationService;
    private final Clock clock;

    // 로컬 테스트에서 지정 월 또는 기본 지난달의 스냅샷 생성을 요청한다.
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public LocalMonthlyReportGenerationResponse generate(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        YearMonth targetMonth = resolveTargetMonth(year, month);
        var result = generationService.generate(targetMonth);
        return new LocalMonthlyReportGenerationResponse(result.targetMonth().getYear(),
                result.targetMonth().getMonthValue(), result.createdCount(), result.skippedCount());
    }

    // 현재·미래 월 스냅샷 생성을 막고 연·월 파라미터의 쌍을 검증한다.
    private YearMonth resolveTargetMonth(Integer year, Integer month) {
        if (year == null && month == null) return YearMonth.now(clock).minusMonths(1);
        if (year == null || month == null) throw new InvalidMonthlyReportGenerationRequestException();
        try {
            YearMonth target = YearMonth.of(year, month);
            if (!target.isBefore(YearMonth.now(clock))) throw new InvalidMonthlyReportGenerationRequestException();
            return target;
        } catch (DateTimeException exception) {
            throw new InvalidMonthlyReportGenerationRequestException();
        }
    }

    public record LocalMonthlyReportGenerationResponse(int year, int month, int createdCount,
            int skippedCount) { }
}
