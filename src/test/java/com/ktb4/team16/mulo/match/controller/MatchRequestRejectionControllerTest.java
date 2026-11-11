package com.ktb4.team16.mulo.match.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.match.service.MatchService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MatchRequestRejectionControllerTest {

    @Mock
    private MatchRequestRepository matchRequestRepository;

    private MockMvc mvc;

    /** 실제 서비스와 예외 처리기를 연결하고 기존 MVC 테스트 방식으로 인증 주체를 설정한다. */
    @BeforeEach
    void setUp() {
        MatchService matchService = new MatchService(matchRequestRepository);
        mvc = MockMvcBuilders.standaloneSetup(new MatchRequestController(matchService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(11L, null, List.of()));
    }

    /** 다른 테스트로 인증 정보가 전파되지 않도록 스레드의 보안 컨텍스트를 비운다. */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 신규 1대1 요청이 저장소 접근 없이 공개 대체 오류 코드와 HTTP 409로 거부되는지 검증한다. */
    @Test
    void rejectsNewOneToOneRequestWithConflictWithoutAccessingRepository() throws Exception {
        mvc.perform(post("/api/match-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteAnswerId\":101}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONE_TO_ONE_MATCHING_REPLACED"))
                .andExpect(jsonPath("$.message").value("신규 채팅 참여는 그룹 채팅을 이용해주세요."));

        verifyNoInteractions(matchRequestRepository);
    }
}
