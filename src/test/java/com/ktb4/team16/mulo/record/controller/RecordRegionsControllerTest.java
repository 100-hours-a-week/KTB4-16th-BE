package com.ktb4.team16.mulo.record.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionGroupResponse;
import com.ktb4.team16.mulo.record.service.RecordService;
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
class RecordRegionsControllerTest {
    @Mock
    private RecordService recordService;

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
    void returnsRegionGroupsWithUnknownGroup() throws Exception {
        when(recordService.getMyRecordRegions(1L)).thenReturn(List.of(
                new RecordRegionGroupResponse("4111710100", "영통동", 5L),
                new RecordRegionGroupResponse("UNKNOWN", "위치 정보 없음", 3L)
        ));

        mvc.perform(get("/api/records/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 자물쇠 지역 그룹 조회 성공"))
                .andExpect(jsonPath("$.data[0].legalDongCode").value("4111710100"))
                .andExpect(jsonPath("$.data[0].legalDongName").value("영통동"))
                .andExpect(jsonPath("$.data[0].recordsCount").value(5))
                .andExpect(jsonPath("$.data[1].legalDongCode").value("UNKNOWN"))
                .andExpect(jsonPath("$.data[1].legalDongName").value("위치 정보 없음"))
                .andExpect(jsonPath("$.data[1].recordsCount").value(3));

        verify(recordService).getMyRecordRegions(1L);
    }

    @Test
    void returnsEmptyDataWhenNoGroupsExist() throws Exception {
        when(recordService.getMyRecordRegions(1L)).thenReturn(List.of());

        mvc.perform(get("/api/records/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 자물쇠 지역 그룹 조회 성공"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
