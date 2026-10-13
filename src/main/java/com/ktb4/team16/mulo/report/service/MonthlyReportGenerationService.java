package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.client.MonthlyReportAiException;
import com.ktb4.team16.mulo.report.client.MonthlyReportBatchAiClient;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MonthlyReportGenerationService {
    private final MonthlyReportPreparationService preparationService;
    private final MonthlyReportBatchAiClient aiClient;
    private final Clock clock;

    public MonthlyReportGenerationService(MonthlyReportPreparationService preparationService,
            MonthlyReportBatchAiClient aiClient, Clock clock) {
        this.preparationService = preparationService;
        this.aiClient = aiClient;
        this.clock = clock;
    }

    // 현재 KST 기준 지난달 스냅샷을 준비하고 AI 배치 생성 요청을 보낸다.
    public GenerationResult generatePreviousMonthReports() {
        return generate(YearMonth.now(clock).minusMonths(1));
    }

    // 스냅샷과 요청 ID를 각각 커밋한 뒤 AI를 호출하고 접수·실패 상태를 반영한다.
    public GenerationResult generate(YearMonth month) {
        var batch = preparationService.prepare(month);
        if (batch.userIds().isEmpty()) {
            return new GenerationResult(month, batch.createdCount(), batch.skippedCount());
        }
        String batchRequestId = UUID.randomUUID().toString();
        preparationService.reserveBatchRequestId(batch, batchRequestId);
        MonthlyReportBatchAiClient.BatchAccepted accepted;
        try {
            accepted = aiClient.requestBatch(month, batch.userIds(), batchRequestId);
        } catch (MonthlyReportAiException exception) {
            String failureType = exception.getCause() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getCause().getClass().getSimpleName();
            log.error("월간 리포트 AI 배치 요청 실패: month={}, targetCount={}, failureType={}",
                    month, batch.userIds().size(), failureType);
            try {
                preparationService.markFailed(batch, batchRequestId);
            } catch (RuntimeException | Error statusException) {
                log.error("월간 리포트 FAILED 상태 저장 롤백: month={}, targetCount={}, failureType={}",
                        month, batch.userIds().size(), statusException.getClass().getSimpleName(),
                        statusException);
                throw statusException;
            }
            return new GenerationResult(month, batch.createdCount(), batch.skippedCount());
        }

        try {
            preparationService.markProcessing(batch, batchRequestId);
        } catch (RuntimeException | Error statusException) {
            log.error("AI 배치 접수 후 PROCESSING 상태 저장 롤백: month={}, targetCount={}, jobId={}, failureType={}",
                    month, batch.userIds().size(), accepted.jobId(),
                    statusException.getClass().getSimpleName(), statusException);
            throw statusException;
        }
        return new GenerationResult(month, batch.createdCount(), batch.skippedCount());
    }

    public record GenerationResult(YearMonth targetMonth, int createdCount, int skippedCount) { }
}
