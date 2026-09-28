package com.ktb4.team16.mulo.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocalMonthlyReportTestGenerationControllerTest {
    private final MonthlyReportGenerationService generationService =
            Mockito.mock(MonthlyReportGenerationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        mvc = MockMvcBuilders.standaloneSetup(
                new LocalMonthlyReportTestGenerationController(generationService, clock))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void generatesCurrentMonthOnlyThroughTheTestEndpoint() throws Exception {
        YearMonth currentMonth = YearMonth.of(2026, 9);
        when(generationService.generate(currentMonth))
                .thenReturn(new MonthlyReportGenerationService.GenerationResult(currentMonth, 1, 0));

        mvc.perform(post("/api/internal/test/monthly-reports/generate")
                        .param("year", "2026")
                        .param("month", "9"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(9))
                .andExpect(jsonPath("$.createdCount").value(1));

        verify(generationService).generate(currentMonth);
    }

    @Test
    void rejectsFutureMonthThroughTheTestEndpoint() throws Exception {
        mvc.perform(post("/api/internal/test/monthly-reports/generate")
                        .param("year", "2026")
                        .param("month", "10"))
                .andExpect(status().isBadRequest());

        verify(generationService, Mockito.never()).generate(any());
    }
}
