package com.ktb4.team16.mulo.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Result;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Status;
import com.ktb4.team16.mulo.report.service.MonthlyReportAiCallbackService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class MonthlyReportAiCallbackControllerTest {
    // 콜백 JSON의 batchRequestId가 검증 서비스까지 같은 값으로 전달되는지 확인한다.
    @Test
    void readsBatchRequestIdFromCallbackJson() throws Exception {
        MonthlyReportAiCallbackService service = mock(MonthlyReportAiCallbackService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new MonthlyReportAiCallbackController(service))
                .build();

        mvc.perform(post("/internal/ai/report-ready")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"batchRequestId":"batch-123","jobId":"job-1","year":2026,"month":8,
                         "generatedAt":"2026-09-01T03:00:00+09:00",
                         "results":[{"userId":7,"status":"FAILED","errorCode":"AI_ERROR"}]}
                        """))
                .andExpect(status().isNoContent());

        ArgumentCaptor<MonthlyReportAiCallbackRequest> captured =
                ArgumentCaptor.forClass(MonthlyReportAiCallbackRequest.class);
        verify(service).apply(captured.capture());
        assertThat(captured.getValue().batchRequestId()).isEqualTo("batch-123");
    }

    // 커밋된 사용자별 AI 실패를 운영자가 월·사용자·오류 코드로 찾을 수 있게 기록한다.
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsFailedUserAfterCallbackIsApplied(CapturedOutput output) {
        MonthlyReportAiCallbackService service = mock(MonthlyReportAiCallbackService.class);
        MonthlyReportAiCallbackController controller = new MonthlyReportAiCallbackController(service);
        MonthlyReportAiCallbackRequest request = callback(
                new Result(7L, Status.FAILED, null, null, "UPSTREAM_UNAVAILABLE"));

        controller.reportReady(request);

        verify(service).apply(request);
        assertThat(output.getOut()).contains(
                "year=2026", "month=8", "userId=7", "errorCode=UPSTREAM_UNAVAILABLE");
    }

    // 콜백 트랜잭션이 롤백되면 실패 월과 대상 수를 로그에 남기고 예외를 유지한다.
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsCallbackRollbackAndRethrows(CapturedOutput output) {
        MonthlyReportAiCallbackService service = mock(MonthlyReportAiCallbackService.class);
        MonthlyReportAiCallbackController controller = new MonthlyReportAiCallbackController(service);
        MonthlyReportAiCallbackRequest request = callback(
                new Result(7L, Status.FAILED, null, null, "UPSTREAM_UNAVAILABLE"));
        RuntimeException failure = new IllegalStateException("callback persistence failed");
        doThrow(failure).when(service).apply(request);

        assertThatThrownBy(() -> controller.reportReady(request)).isSameAs(failure);
        assertThat(output.getOut()).contains(
                "year=2026", "month=8", "resultCount=1", "callback persistence failed");
    }

    // 월별 콜백 실패 로그 테스트에서 같은 요청 메타데이터를 사용한다.
    private MonthlyReportAiCallbackRequest callback(Result result) {
        return new MonthlyReportAiCallbackRequest(null, "job-1", 2026, 8,
                OffsetDateTime.parse("2026-09-01T03:00:00+09:00"), List.of(result));
    }
}
