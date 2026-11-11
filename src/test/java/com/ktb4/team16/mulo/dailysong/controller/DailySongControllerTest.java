package com.ktb4.team16.mulo.dailysong.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.dailysong.dto.DailySongDetailResponse;
import com.ktb4.team16.mulo.dailysong.service.DailySongQueryService;
import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.notification.exception.NotificationDomainException;
import java.time.LocalDate;
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
class DailySongControllerTest {
    @Mock
    private DailySongQueryService dailySongQueryService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DailySongController(dailySongQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(35L, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDailySongUsesAuthenticatedUserAndReturnsTrackDetailsWithoutRecommendationReason() throws Exception {
        when(dailySongQueryService.getDailySong(35L, 803L)).thenReturn(new DailySongDetailResponse(
                "오늘의 노래 조회 성공",
                new DailySongDetailResponse.Data(803L, LocalDate.of(2026, 11, 11),
                        new DailySongDetailResponse.Track("1234567890123456789012", "테스트 곡", "테스트 가수",
                                "https://example.test/album", "https://example.test/track"))));

        mvc.perform(get("/api/daily-songs/803").param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("오늘의 노래 조회 성공"))
                .andExpect(jsonPath("$.data.jobId").value(803))
                .andExpect(jsonPath("$.data.track.title").value("테스트 곡"))
                .andExpect(jsonPath("$.data.track.artistName").value("테스트 가수"))
                .andExpect(jsonPath("$.data.track.albumImageUrl").value("https://example.test/album"))
                .andExpect(jsonPath("$.data.recommendReason").doesNotExist());

        verify(dailySongQueryService).getDailySong(35L, 803L);
    }

    @Test
    void returnsNotFoundWhenDailySongIsNotAvailableToTheUser() throws Exception {
        when(dailySongQueryService.getDailySong(35L, 803L))
                .thenThrow(new NotificationDomainException(ErrorCode.AI_RECOMMENDATION_NOT_FOUND));

        mvc.perform(get("/api/daily-songs/803"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_RECOMMENDATION_NOT_FOUND"));

        verify(dailySongQueryService).getDailySong(35L, 803L);
    }
}
