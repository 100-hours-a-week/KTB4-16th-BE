package com.ktb4.team16.mulo.report.controller;

import com.ktb4.team16.mulo.report.batch.MonthlyReportBatchStore;
import com.ktb4.team16.mulo.report.batch.MonthlyReportJobService;
import jakarta.validation.constraints.Positive;
import com.ktb4.team16.mulo.report.batch.MonthlyReportDispatchService;
import com.ktb4.team16.mulo.report.batch.ReportBatchException;
import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 토큰으로 보호되는 실패분 재요청·접수 확인·업무 상태 조회 진입점이다. */
@RestController
@ConditionalOnProperty("report.operation.token")
@RequestMapping("/internal/ops/monthly-reports")
public class ReportOperationBatchController {
    private final MonthlyReportGenerationService generation;
    private final MonthlyReportDispatchService dispatch;
    private final MonthlyReportBatchStore store;
    private final MonthlyReportJobService jobs;

    /** 월 정책·전송 복구·읽기 모델의 책임을 각 서비스에 위임한다. */
    public ReportOperationBatchController(MonthlyReportGenerationService generation,
            MonthlyReportDispatchService dispatch, MonthlyReportBatchStore store, MonthlyReportJobService jobs) {
        this.generation = generation;
        this.dispatch = dispatch;
        this.store = store;
        this.jobs = jobs;
    }

    /** 새 UUID의 재요청 등록만 명단을 선정하며 중복 등록은 기존 결과를 돌려준다. */
    @PostMapping("/retry")
    public MonthlyReportGenerationService.GenerationResult retry(@Valid @RequestBody RetryRequest request) {
        try {
            return generation.retry(YearMonth.of(request.year(), request.month()), request.retryRequestId());
        } catch (DateTimeException exception) {
            throw new ReportBatchException("REPORT_INVALID_REQUEST");
        }
    }

    /** AI팀 확인 근거를 저장하며 이 호출 자체가 AI 전송을 실행하지 않는다. */
    @PostMapping("/confirm-dispatch")
    public void confirm(@Valid @RequestBody Confirmation request) {
        dispatch.reconcile(request.batchRequestId(), request.accepted(), request.actor(), request.evidence());
    }

    /** 확인된 미접수 또는 이미 접수된 기존 실행을 같은 식별값으로 이어간다. */
    @PostMapping("/resume")
    public MonthlyReportGenerationService.GenerationResult resume(@Valid @RequestBody ResumeRequest request) {
        return jobs.resume(request.runKey());
    }

    /** 종료 확인은 운영자의 책임이며 이 요청은 전송하거나 진행 중인 프로세스를 종료하지 않는다. */
    @PostMapping("/recover-stopped-execution")
    public void recover(@Valid @RequestBody Recovery request) {
        jobs.recoverStopped(request.runKey(), request.executionId(), request.stoppedConfirmed(),
                request.actor(), request.evidence());
    }

    /** runKey는 백엔드 실행 참조이며 AI 요청 식별자인 batchRequestId와 구분한다. */
    @GetMapping("/runs/{runKey}")
    public RunView status(@PathVariable String runKey) {
        return new RunView(store.run(runKey), store.dispatch(runKey));
    }

    /** 운영 도구는 재전달 동안 같은 retryRequestId를 보존해야 한다. */
    public record RetryRequest(@Min(2026) int year, @Min(1) @Max(12) int month,
            @NotBlank String retryRequestId) { }
    /** 미접수 확정은 사용자 추측이 아니라 AI 기록 대조 증거가 필요하다. */
    public record Confirmation(@NotBlank @Size(max = 36) String batchRequestId,
            @NotNull Boolean accepted, @NotBlank @Size(max = 100) String actor,
            @NotBlank @Size(max = 500) String evidence) { }
    /** 지난달 제한을 적용하는 새 생성과 구분해 기존 runKey만 재개한다. */
    public record ResumeRequest(@NotBlank @Size(max = 100) String runKey) { }
    /** 실행 키·실행 ID와 프로세스 종료 확인 및 확인 근거를 함께 받는다. */
    public record Recovery(@NotBlank @Size(max = 100) String runKey, @Positive Long executionId,
            @NotNull Boolean stoppedConfirmed, @NotBlank @Size(max = 100) String actor,
            @NotBlank @Size(max = 500) String evidence) { }
    /** 기존 성공 건수 응답을 바꾸지 않고 추가 운영 경로에서 전송 상태를 제공한다. */
    public record RunView(MonthlyReportBatchStore.Run run, MonthlyReportBatchStore.Dispatch dispatch) { }
}
