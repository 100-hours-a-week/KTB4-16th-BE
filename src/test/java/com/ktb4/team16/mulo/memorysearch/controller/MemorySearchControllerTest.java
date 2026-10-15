package com.ktb4.team16.mulo.memorysearch.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.memorysearch.service.MemorySearchService;
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
class MemorySearchControllerTest {

    @Mock
    private MemorySearchService service;

    private MockMvc mvc;

    /** 인증된 사용자 ID를 질문 API에 전달하는 MVC 경계를 만든다. */
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MemorySearchController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        // 인증 주체만 검증하므로 자격 증명 값은 테스트에 필요하지 않다.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(35L, null, List.of()));
    }

    /** 테스트마다 인증 정보를 제거한다. */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 질문 응답은 message/data와 카드 배열을 공개하고 AI 문맥은 요청받지 않는다. */
    @Test
    void returnsSearchCardsInPublicEnvelope() throws Exception {
        String conversationId = "550e8400-e29b-41d4-a716-446655440000";
        // 날씨가 없는 기록의 공개 카드 값은 null이다.
        var card = new MemorySearchCard(9L, 109L, "밤편지", "아이유",
                209L, "성수동", "2026.10.15", null, "");
        when(service.search(35L, conversationId, "가을 노래"))
                .thenReturn(new MemorySearchResult("기억을 살펴봤어요.",
                        List.of(card), List.of()));

        mvc.perform(post("/api/memories/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversationId
                                + "\",\"query\":\"가을 노래\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("기억을 살펴봤어요."))
                .andExpect(jsonPath("$.data.records[0].recordId").value(9))
                .andExpect(jsonPath("$.data.records[0].date").value("2026.10.15"))
                .andExpect(jsonPath("$.data.suggestedRecords").isArray());
    }
}
