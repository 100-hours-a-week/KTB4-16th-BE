package com.ktb4.team16.mulo.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.recommendation.dto.request.AiRecommendationFeedbackRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.AiRecommendationFeedbackResponse;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedbackType;
import com.ktb4.team16.mulo.recommendation.service.AiRecommendationFeedbackService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AiRecommendationFeedbackControllerTest {

    @Mock private AiRecommendationFeedbackService feedbackService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AiRecommendationFeedbackController(feedbackService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(17L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsCreatedWithStoredFeedbackData() throws Exception {
        when(feedbackService.submit(eq(17L), eq(42L), any(AiRecommendationFeedbackRequest.class)))
                .thenReturn(new AiRecommendationFeedbackResponse("추천 피드백을 제출했습니다.",
                        new AiRecommendationFeedbackResponse.Data(7L, 42L,
                                AiRecommendationFeedbackType.LIKE, "3Ml2s37uS9jqRM2R3bfDiB")));

        mvc.perform(post("/api/ai-recommendations/42/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedback":"LIKE","selectedTrackId":"3Ml2s37uS9jqRM2R3bfDiB"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("추천 피드백을 제출했습니다."))
                .andExpect(jsonPath("$.data.feedbackId").value(7))
                .andExpect(jsonPath("$.data.recommendationId").value(42))
                .andExpect(jsonPath("$.data.feedback").value("LIKE"))
                .andExpect(jsonPath("$.data.selectedTrackId").value("3Ml2s37uS9jqRM2R3bfDiB"));

        verify(feedbackService).submit(eq(17L), eq(42L), any(AiRecommendationFeedbackRequest.class));
    }

    @Test
    void returnsBadRequestWhenFeedbackIsMissing() throws Exception {
        mvc.perform(post("/api/ai-recommendations/42/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(feedbackService);
    }

    @Test
    void returnsBadRequestForUnknownFeedbackValue() throws Exception {
        mvc.perform(post("/api/ai-recommendations/42/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(feedbackService);
    }

    @Test
    void returnsBadRequestForBlankTrackId() throws Exception {
        mvc.perform(post("/api/ai-recommendations/42/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"LIKE\",\"selectedTrackId\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(feedbackService);
    }

    @Test
    void returnsBadRequestForInvalidRecommendationId() throws Exception {
        mvc.perform(post("/api/ai-recommendations/0/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"DISLIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verifyNoInteractions(feedbackService);
    }
}
