package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;

import com.ktb4.team16.mulo.report.client.MonthlyReportBatchAiClient;
import com.ktb4.team16.mulo.report.client.MonthlyReportAiException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyReportGenerationServiceTest {
    @Mock private MonthlyReportPreparationService preparationService;
    @Mock private MonthlyReportBatchAiClient aiClient;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void marksPreparedReportsProcessingAfterAiQueuesBatch() {
        YearMonth target = YearMonth.of(2026, 8);
        var batch = new MonthlyReportPreparationService.PreparedBatch(target, List.of(7L), 1, 0);
        when(preparationService.prepare(target)).thenReturn(batch);
        when(aiClient.requestBatch(eq(target), eq(List.of(7L)), anyString()))
                .thenReturn(new MonthlyReportBatchAiClient.BatchAccepted("report_batch_2026-8", 1));

        var result = new MonthlyReportGenerationService(preparationService, aiClient, clock)
                .generate(target);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(preparationService, aiClient);
        order.verify(preparationService).reserveBatchRequestId(eq(batch), requestId.capture());
        order.verify(aiClient).requestBatch(target, List.of(7L), requestId.getValue());
        verify(preparationService).markProcessing(batch, requestId.getValue());
        assertThat(requestId.getValue()).hasSize(36);
        assertThat(result.createdCount()).isEqualTo(1);
    }

    // 배치 접수 실패 시 선저장 통계를 유지하도록 실패 상태 전환만 요청한다.
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void marksPreparedReportsFailedAndLogsTargetCountWhenAiRejectsBatch(CapturedOutput output) {
        YearMonth target = YearMonth.of(2026, 8);
        var batch = new MonthlyReportPreparationService.PreparedBatch(target, List.of(7L, 8L), 2, 0);
        when(preparationService.prepare(target)).thenReturn(batch);
        when(aiClient.requestBatch(eq(target), eq(List.of(7L, 8L)), anyString()))
                .thenThrow(new MonthlyReportAiException());

        var result = new MonthlyReportGenerationService(preparationService, aiClient, clock)
                .generate(target);

        verify(preparationService).markFailed(eq(batch), anyString());
        verify(preparationService, never()).markProcessing(eq(batch), anyString());
        assertThat(result.createdCount()).isEqualTo(2);
        assertThat(output.getOut()).contains("month=2026-08", "targetCount=2");
    }

    // AI 접수 뒤 PROCESSING 저장 트랜잭션이 롤백되면 작업 식별자와 함께 예외를 전파한다.
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsAcceptedJobWhenProcessingStatusRollsBack(CapturedOutput output) {
        YearMonth target = YearMonth.of(2026, 8);
        var batch = new MonthlyReportPreparationService.PreparedBatch(target, List.of(7L, 8L), 2, 0);
        when(preparationService.prepare(target)).thenReturn(batch);
        when(aiClient.requestBatch(eq(target), eq(List.of(7L, 8L)), anyString()))
                .thenReturn(new MonthlyReportBatchAiClient.BatchAccepted("job-123", 2));
        RuntimeException failure = new IllegalStateException("status persistence failed");
        doThrow(failure).when(preparationService).markProcessing(eq(batch), anyString());

        MonthlyReportGenerationService service = new MonthlyReportGenerationService(
                preparationService, aiClient, clock);

        assertThatThrownBy(() -> service.generate(target)).isSameAs(failure);
        assertThat(output.getOut()).contains(
                "month=2026-08", "targetCount=2", "jobId=job-123");
    }

    // AI 요청 실패 후 FAILED 상태 저장도 롤백되면 월과 대상 수를 기록하고 예외를 전파한다.
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsFailedStatusRollbackAfterAiRequestFailure(CapturedOutput output) {
        YearMonth target = YearMonth.of(2026, 8);
        var batch = new MonthlyReportPreparationService.PreparedBatch(target, List.of(7L, 8L), 2, 0);
        when(preparationService.prepare(target)).thenReturn(batch);
        when(aiClient.requestBatch(eq(target), eq(List.of(7L, 8L)), anyString()))
                .thenThrow(new MonthlyReportAiException());
        RuntimeException failure = new IllegalStateException("failed status persistence failed");
        doThrow(failure).when(preparationService).markFailed(eq(batch), anyString());

        MonthlyReportGenerationService service = new MonthlyReportGenerationService(
                preparationService, aiClient, clock);

        assertThatThrownBy(() -> service.generate(target)).isSameAs(failure);
        assertThat(output.getOut()).contains(
                "month=2026-08", "targetCount=2", "FAILED 상태 저장 롤백");
    }
}
