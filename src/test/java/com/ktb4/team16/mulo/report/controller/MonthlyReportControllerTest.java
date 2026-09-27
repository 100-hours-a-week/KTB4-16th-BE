package com.ktb4.team16.mulo.report.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.report.entity.MonthlyReport;
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
    @Mock private MonthlyReport report;
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
        when(report.getMonthlyReportId()).thenReturn(3L);
        when(report.getReportYear()).thenReturn((short) 2026);
        when(report.getReportMonth()).thenReturn((short) 8);
        when(report.getRecordCount()).thenReturn(15);
        when(report.getAiRecapStatus()).thenReturn(MonthlyReport.AiRecapStatus.COMPLETED);
        when(queryService.getReports(7L)).thenReturn(List.of(report));

        mvc.perform(get("/api/monthly-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports[0].monthlyReportId").value(3))
                .andExpect(jsonPath("$.data.reports[0].year").value(2026));
    }

    @Test
    void hidesAnotherUsersReportAsNotFound() throws Exception {
        when(queryService.getDetail(7L, 99L)).thenThrow(new MonthlyReportNotFoundException());
        mvc.perform(get("/api/monthly-reports/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONTHLY_REPORT_NOT_FOUND"));
    }
}
