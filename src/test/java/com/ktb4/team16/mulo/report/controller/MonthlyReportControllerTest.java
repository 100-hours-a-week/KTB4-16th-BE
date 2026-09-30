package com.ktb4.team16.mulo.report.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.report.dto.response.MonthlyReportListResponse;
import com.ktb4.team16.mulo.report.dto.response.MonthlyReportDetailResponse;
import java.math.BigDecimal;
import com.ktb4.team16.mulo.report.exception.MonthlyReportNotFoundException;
import com.ktb4.team16.mulo.report.service.MonthlyReportQueryService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MonthlyReportControllerTest {
    @Mock private MonthlyReportQueryService queryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MonthlyReportController(queryService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void returnsReportsInQueryServiceOrder() throws Exception {
        when(queryService.getReports(7L)).thenReturn(List.of(
                new MonthlyReportListResponse.Report(3L, 2026, 8, 15, "COMPLETED")));

        mvc.perform(get("/api/monthly-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports[0].monthlyReportId").value(3))
                .andExpect(jsonPath("$.data.reports[0].year").value(2026));
    }

    // 엔티티 없이 완성된 상세 DTO를 기존 JSON 계약으로 반환한다.
    @Test
    void returnsDetailWithTopPlaceNameFromValueDto() throws Exception {
        var data = new MonthlyReportDetailResponse.Data(3L, 2026, 8,
                new MonthlyReportDetailResponse.Stats(4,
                        new MonthlyReportDetailResponse.TopPlace(9L, "테스트동"),
                        "가수", new BigDecimal("15.0")),
                List.of(), new MonthlyReportDetailResponse.AiRecap("COMPLETED", "8월 회고"));
        when(queryService.getDetail(7L, 3L)).thenReturn(data);

        mvc.perform(get("/api/monthly-reports/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.topPlace.placeId").value(9))
                .andExpect(jsonPath("$.data.stats.topPlace.legalDongName").value("테스트동"))
                .andExpect(jsonPath("$.data.aiRecap.status").value("COMPLETED"));
    }

    @Test
    void hidesAnotherUsersReportAsNotFound() throws Exception {
        when(queryService.getDetail(7L, 99L)).thenThrow(new MonthlyReportNotFoundException());
        mvc.perform(get("/api/monthly-reports/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONTHLY_REPORT_NOT_FOUND"));
    }
}
