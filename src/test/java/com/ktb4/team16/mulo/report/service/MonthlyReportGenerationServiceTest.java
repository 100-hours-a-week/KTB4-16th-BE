package com.ktb4.team16.mulo.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.report.client.MonthlyReportBatchAiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(aiClient.requestBatch(target, List.of(7L)))
                .thenReturn(new MonthlyReportBatchAiClient.BatchAccepted("report_batch_2026-8", 1));

        var result = new MonthlyReportGenerationService(preparationService, aiClient, clock)
                .generate(target);

        verify(preparationService).markProcessing(batch);
        assertThat(result.createdCount()).isEqualTo(1);
    }
}
