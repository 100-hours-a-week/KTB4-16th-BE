package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb4.team16.mulo.report.batch.MonthlyReportJobService;
import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportGenerationRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** 기존 직접 AI 호출 테스트는 영속 Job 위임·운영 월 정책 검증으로 전환한다. */
class MonthlyReportGenerationServiceTest {
    private final MonthlyReportJobService jobs = mock(MonthlyReportJobService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    /** 정기 실행과 지난달 수동 실행은 동일한 생성 Job 인자를 사용한다. */
    @Test
    void scheduledGenerationUsesPreviousMonthJob() {
        new MonthlyReportGenerationService(jobs, clock).generatePreviousMonthReports();
        verify(jobs).generate(YearMonth.of(2026,8), false);
    }

    /** API 외의 호출도 현재 월·과거 월 신규 생성을 우회할 수 없다. */
    @Test
    void rejectsCurrentAndOldMonthBeforeStartingJob() {
        var service = new MonthlyReportGenerationService(jobs, clock);
        assertThatThrownBy(() -> service.generate(YearMonth.of(2026,9)))
                .isInstanceOf(InvalidMonthlyReportGenerationRequestException.class);
        assertThatThrownBy(() -> service.generate(YearMonth.of(2026,7)))
                .isInstanceOf(InvalidMonthlyReportGenerationRequestException.class);
        verifyNoInteractions(jobs);
    }
}
