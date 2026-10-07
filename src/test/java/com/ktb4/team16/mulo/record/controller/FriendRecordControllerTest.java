package com.ktb4.team16.mulo.record.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.friend.exception.FriendDomainException;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.record.dto.response.FriendRecordDashboardData;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsData;
import com.ktb4.team16.mulo.record.service.FriendRecordReadService;
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
class FriendRecordControllerTest {

    @Mock
    private FriendRecordReadService friendRecordReadService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FriendRecordController(friendRecordReadService))
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
    void returnsFriendDashboardEnvelopeIncludingEmptyRegions() throws Exception {
        when(friendRecordReadService.getDashboard(1L, 2L)).thenReturn(
                new FriendRecordDashboardData(2L, "친구", 0L, List.of()));

        mvc.perform(get("/api/users/2/records/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 자물쇠 지역 그룹 조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.nickname").value("친구"))
                .andExpect(jsonPath("$.data.recordsCount").value(0))
                .andExpect(jsonPath("$.data.regions").isArray())
                .andExpect(jsonPath("$.data.regions").isEmpty());
    }

    @Test
    void returnsFriendRegionPageWithCurrentCursor() throws Exception {
        when(friendRecordReadService.getRegionRecords(1L, 2L, "UNKNOWN", "cursor"))
                .thenReturn(new RecordRegionRecordsData(
                        "UNKNOWN", "위치 정보 없음", 0L, List.of(), null));

        mvc.perform(get("/api/users/2/records")
                        .param("legalDongCode", "UNKNOWN")
                        .param("cursor", "cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 지역 자물쇠 목록 조회 성공"))
                .andExpect(jsonPath("$.data.legalDongCode").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.records").isEmpty());

        verify(friendRecordReadService).getRegionRecords(1L, 2L, "UNKNOWN", "cursor");
    }

    @Test
    void mapsNonFriendTargetToHiddenNotFoundResponse() throws Exception {
        when(friendRecordReadService.getDashboard(1L, 2L))
                .thenThrow(new FriendDomainException(ErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/api/users/2/records/regions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
