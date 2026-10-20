package com.ktb4.team16.mulo.match.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestCreateResponse;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestStatusResponse;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.service.MatchService;
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
class MatchRequestControllerTest {

    @Mock
    private MatchService matchService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MatchRequestController(matchService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(11L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsMatchRequestForAuthenticatedPrincipal() throws Exception {
        when(matchService.createMatchRequest(11L, new MatchRequestCreateRequest(101L)))
                .thenReturn(new MatchRequestCreateResponse(
                        "매칭 요청 생성 성공",
                        new MatchRequestCreateResponse.Data(301L, MatchRequestStatus.WAITING)));

        mvc.perform(post("/api/match-requests")
                        .contentType("application/json")
                        .content("{\"voteAnswerId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("매칭 요청 생성 성공"))
                .andExpect(jsonPath("$.data.matchRequestId").value(301))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        verify(matchService).createMatchRequest(11L, new MatchRequestCreateRequest(101L));
    }

    @Test
    void returnsMatchRequestStatusForAuthenticatedPrincipal() throws Exception {
        when(matchService.getMatchRequestStatus(11L, 301L))
                .thenReturn(new MatchRequestStatusResponse(
                        "매칭 요청 상태 조회 성공",
                        new MatchRequestStatusResponse.Data(301L, MatchRequestStatus.WAITING, null)));

        mvc.perform(get("/api/match-requests/{matchRequestId}", 301L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매칭 요청 상태 조회 성공"))
                .andExpect(jsonPath("$.data.matchRequestId").value(301))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.chatRoomId").value(nullValue()));

        verify(matchService).getMatchRequestStatus(11L, 301L);
    }

    @Test
    void returnsMatchRequestStatusByVoteAnswerForAuthenticatedPrincipal() throws Exception {
        when(matchService.getMatchRequestStatusByVoteAnswer(11L, 101L))
                .thenReturn(new MatchRequestStatusResponse(
                        "매칭 요청 상태 조회 성공",
                        new MatchRequestStatusResponse.Data(301L, MatchRequestStatus.FAILED, null)));

        mvc.perform(get("/api/match-requests/by-vote-answer/{voteAnswerId}", 101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchRequestId").value(301))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.chatRoomId").value(nullValue()));

        verify(matchService).getMatchRequestStatusByVoteAnswer(11L, 101L);
    }

    @Test
    void forwardsMatchRequestStatusDomainException() throws Exception {
        when(matchService.getMatchRequestStatus(11L, 301L))
                .thenThrow(new MatchDomainException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        mvc.perform(get("/api/match-requests/{matchRequestId}", 301L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("매칭 요청을 찾을 수 없습니다."));
    }

    @Test
    void rejectsRequestWithoutVoteAnswerId() throws Exception {
        mvc.perform(post("/api/match-requests")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
