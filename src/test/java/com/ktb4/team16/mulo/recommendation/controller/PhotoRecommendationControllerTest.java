package com.ktb4.team16.mulo.recommendation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.recommendation.dto.response.PhotoRecommendationResponse;
import com.ktb4.team16.mulo.recommendation.service.PhotoRecommendationService;
import java.util.List;
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
class PhotoRecommendationControllerTest {
    @Mock private PhotoRecommendationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PhotoRecommendationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    }

    @Test
    void returnsRecommendations() throws Exception {
        when(service.recommend(7L, 123L)).thenReturn(new PhotoRecommendationService.RecommendationResult(44L, List.of(
                new PhotoRecommendationResponse.Track("id", "title", "artist", "album", "url")), "  사진 🌄\n그대로  "));

        mvc.perform(post("/api/recommendations/photo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사진 기반 음악 추천 성공"))
                .andExpect(jsonPath("$.recommendationId").value(44))
                .andExpect(jsonPath("$.recommendReason").value("  사진 🌄\n그대로  "))
                .andExpect(jsonPath("$.data[0].externalTrackId").value("id"));

        verify(service).recommend(7L, 123L);
    }

    // AI가 이유를 주지 않아도 기존 추천은 성공하고 공개 필드 값만 null이 된다.
    @Test
    void returnsNullReasonWithoutChangingTrackArray() throws Exception {
        when(service.recommend(7L, 123L)).thenReturn(new PhotoRecommendationService.RecommendationResult(
                45L, List.of(new PhotoRecommendationResponse.Track("id", "title", "artist", "album", "url")), null));

        mvc.perform(post("/api/recommendations/photo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uploadId\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendReason").hasJsonPath())
                .andExpect(jsonPath("$.recommendReason").isEmpty())
                .andExpect(jsonPath("$.recommendationId").value(45))
                .andExpect(jsonPath("$.data[0].externalTrackId").value("id"));
    }

    @Test
    void missingUploadIdReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/recommendations/photo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
}
