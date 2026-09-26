package com.ktb4.team16.mulo.record.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.service.RecordService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecordRegionRecordsControllerTest {
    private final RecordService recordService = mock(RecordService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecordController(recordService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsRegionRecordsAndNextCursor() throws Exception {
        when(recordService.getMyRecords(1L, "4111710100", "cursor"))
                .thenReturn(new RecordRegionRecordsData(
                        "4111710100", "영통동", 21L, List.of(), "next"));

        mvc.perform(get("/api/records")
                        .param("legalDongCode", "4111710100")
                        .param("cursor", "cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 자물쇠 목록 조회 성공"))
                .andExpect(jsonPath("$.data.legalDongCode").value("4111710100"))
                .andExpect(jsonPath("$.data.recordsCount").value(21))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.nextCursor").value("next"));

        verify(recordService).getMyRecords(1L, "4111710100", "cursor");
    }

    @Test
    void missingLegalDongCodeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/records"))
                .andExpect(status().isBadRequest());
    }
}
