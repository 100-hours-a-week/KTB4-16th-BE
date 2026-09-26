package com.ktb4.team16.mulo.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaylistCommandService;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaylistQueryService;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
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
class RecommendationPlaylistControllerTest {
    @Mock private RecommendationPlaylistQueryService queryService;
    @Mock private RecommendationPlaylistCommandService commandService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationPlaylistController(queryService, commandService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSavedPlaylistForAuthenticatedUser() throws Exception {
        authenticate(7L);
        when(queryService.getCurrentPlaylist(7L)).thenReturn(new RecommendationPlaylistData(
                new RecommendationPlaylistData.Playlist(3L, List.of(
                        new RecommendationPlaylistData.Track(10L, "밤편지", "아이유", "url")))));

        mvc.perform(get("/api/recommendations/playlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("현재 추천 플레이리스트 조회 성공"))
                .andExpect(jsonPath("$.data.playlist.recommendationPlaylistId").value(3))
                .andExpect(jsonPath("$.data.playlist.tracks[0].title").value("밤편지"));
    }

    @Test
    void createsPlaylistFromCoordinate() throws Exception {
        authenticate(7L);
        RecommendationPlaylistData.Playlist playlist = new RecommendationPlaylistData.Playlist(4L,
                List.of(new RecommendationPlaylistData.Track(10L, "밤편지", "아이유", "url")));
        when(commandService.create(eq(7L), any())).thenReturn(playlist);

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("추천 플레이리스트 생성 성공"))
                .andExpect(jsonPath("$.data.playlist.recommendationPlaylistId").value(4));
    }

    @Test
    void missingLatitudeReturnsLatitudeRequiredError() throws Exception {
        authenticate(7L);

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":126.9780}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].code").value("LATITUDE_REQUIRED"));
    }

    @Test
    void convertsWeatherFailureToBadGateway() throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenThrow(new WeatherApiException());

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("WEATHER_API_ERROR"));
    }

    // 인증 필터가 저장한 사용자 식별자를 컨트롤러 테스트에 설정한다.
    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
