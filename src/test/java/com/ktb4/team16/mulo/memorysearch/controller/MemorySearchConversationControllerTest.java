package com.ktb4.team16.mulo.memorysearch.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.memorysearch.service.MemorySearchConversationService;
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
class MemorySearchConversationControllerTest {

    @Mock
    private MemorySearchConversationService service;

    private MockMvc mvc;

    /** 인증 사용자 ID가 전달되는 독립 컨트롤러 테스트 환경을 구성한다. */
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MemorySearchConversationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(35L, null, List.of()));
    }

    /** 테스트 간 인증 사용자가 남지 않도록 제거한다. */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 대화 시작은 본인 소유 ID를 발급해 201 data.conversationId로 반환한다. */
    @Test
    void startsConversationForAuthenticatedUser() throws Exception {
        when(service.start(35L)).thenReturn("ca4e5334-6756-4107-b38b-f2674cdbcbbe");

        mvc.perform(post("/api/memories/search/conversations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId")
                        .value("ca4e5334-6756-4107-b38b-f2674cdbcbbe"))
                .andExpect(jsonPath("$.message").isString());

        verify(service).start(35L);
    }

    /** 명시적 나가기는 인증 사용자와 대화 ID만 전달하고 204 본문 없이 완료한다. */
    @Test
    void closesConversationForAuthenticatedUser() throws Exception {
        String conversationId = "ca4e5334-6756-4107-b38b-f2674cdbcbbe";

        mvc.perform(delete("/api/memories/search/conversations/{conversationId}",
                        conversationId))
                .andExpect(status().isNoContent());

        verify(service).close(35L, conversationId);
    }
}
