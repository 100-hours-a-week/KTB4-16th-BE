package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.batch.MonthlyReportJobService;
import com.ktb4.team16.mulo.report.batch.MonthlyReportTestGuard;
import com.ktb4.team16.mulo.report.exception.InvalidMonthlyReportGenerationRequestException;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 스케줄러·운영 API의 월 정책을 검증하고 실제 실행을 영속 Batch Job에 위임한다. */
@Service
public class MonthlyReportGenerationService {
    private final MonthlyReportJobService jobs;
    private final Clock clock;
    private final MonthlyReportTestGuard testGuard;

    /** 월 정책 단위 검증에 필요한 생성자다. 테스트 실행은 기본 거절한다. */
    public MonthlyReportGenerationService(MonthlyReportJobService jobs, Clock clock) {
        this(jobs, clock, null);
    }

    /** 실제 테스트 진입점은 프로파일·전용 DB·AI 대상 검사를 추가한다. */
    @Autowired
    public MonthlyReportGenerationService(MonthlyReportJobService jobs, Clock clock, MonthlyReportTestGuard testGuard) {
        this.jobs = jobs;
        this.clock = clock;
        this.testGuard = testGuard;
    }

    /** KST Clock 기준 지난달 마감 Job을 실행한다. */
    public GenerationResult generatePreviousMonthReports() {
        return generate(YearMonth.now(clock).minusMonths(1));
    }

    /** 운영 신규 생성은 지난달만 허용한다. */
    public GenerationResult generate(YearMonth month) {
        if (!YearMonth.now(clock).minusMonths(1).equals(month)) {
            throw new InvalidMonthlyReportGenerationRequestException();
        }
        return jobs.generate(month, false);
    }

    /** 현재 월까지의 시험은 격리 환경 확인 뒤 별도 작업 키로 실행한다. */
    public GenerationResult generateForTest(YearMonth month) {
        if (testGuard == null || month == null || month.isAfter(YearMonth.now(clock))) {
            throw new InvalidMonthlyReportGenerationRequestException();
        }
        testGuard.verify();
        return jobs.generate(month, true);
    }

    /** 실패 확정 리포트는 과거 마감 월도 기존 집계를 보존하며 재요청한다. */
    public GenerationResult retry(YearMonth month, String retryRequestId) {
        if (month == null || !month.isBefore(YearMonth.now(clock))) {
            throw new InvalidMonthlyReportGenerationRequestException();
        }
        return jobs.retry(month, retryRequestId);
    }

    /** 기존 동기 생성 API의 집계 건수 의미를 유지한다. */
    public record GenerationResult(YearMonth targetMonth, int createdCount, int skippedCount) { }
}
