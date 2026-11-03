package com.ktb4.team16.mulo.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.recommendation.client.RecommendationAiException;
import com.ktb4.team16.mulo.recommendation.dto.request.CreateRecommendationPlaylistRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.RecommendationPlaylistData;
import com.ktb4.team16.mulo.recommendation.service.RecommendationPlaylistCommandService;
import com.ktb4.team16.mulo.weather.exception.WeatherApiException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private RecommendationPlaylistCommandService commandService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationPlaylistController(commandService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // 화면 세션 추천은 서버에 저장된 목록을 복원하는 GET을 제공하지 않는다.
    @Test
    void doesNotExposeSavedPlaylistLookup() throws Exception {
        authenticate(7L);

        mvc.perform(get("/api/recommendations/playlists"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void createsPlaylistFromCoordinate() throws Exception {
        authenticate(7L);
        RecommendationPlaylistData.Playlist playlist = new RecommendationPlaylistData.Playlist(
                List.of(new RecommendationPlaylistData.Track(10L, "external-10", "밤편지", "아이유", "image-url", "url")));
        when(commandService.create(eq(7L), any())).thenReturn(
                new RecommendationPlaylistCommandService.CreateResult(playlist, true, 44L, "  AI 이유 🌙\n그대로  "));

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("추천 플레이리스트 생성 성공"))
                .andExpect(jsonPath("$.recommendationId").value(44))
                .andExpect(jsonPath("$.recommendReason").value("  AI 이유 🌙\n그대로  "))
                .andExpect(jsonPath("$.data.playlist.recommendationPlaylistId").doesNotExist())
                .andExpect(jsonPath("$.data.playlist.tracks[0].musicTrackId").value(10))
                .andExpect(jsonPath("$.data.replaced").value(true));
    }

    @Test
    void leavesCurrentScreenStateWithFrontendWhenAiIsDegraded() throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenReturn(
                new RecommendationPlaylistCommandService.CreateResult(null, false, null, null));

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("추천 결과가 없어 기존 플레이리스트 유지"))
                .andExpect(jsonPath("$.recommendationId").doesNotExist())
                .andExpect(jsonPath("$.data.playlist").doesNotExist())
                .andExpect(jsonPath("$.recommendReason").hasJsonPath())
                .andExpect(jsonPath("$.recommendReason").isEmpty())
                .andExpect(jsonPath("$.data.replaced").value(false));
    }

    // 추천 이유가 없는 정상 응답도 기존 곡·평가 ID를 유지하고 null 필드를 반환한다.
    @Test
    void createsPlaylistWithoutRecommendationReason() throws Exception {
        authenticate(7L);
        var playlist = new RecommendationPlaylistData.Playlist(List.of(
                new RecommendationPlaylistData.Track(10L, "external-10", "밤편지", "아이유", "image-url", "url")));
        when(commandService.create(eq(7L), any())).thenReturn(
                new RecommendationPlaylistCommandService.CreateResult(playlist, true, 45L, null));

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendReason").hasJsonPath())
                .andExpect(jsonPath("$.recommendReason").isEmpty())
                .andExpect(jsonPath("$.recommendationId").value(45))
                .andExpect(jsonPath("$.data.playlist.tracks[0].musicTrackId").value(10));
    }

    @Test
    void missingLatitudeReturnsLatitudeRequiredError() throws Exception {
        authenticate(7L);

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":126.9780,\"recommendationAttempt\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].code").value("LATITUDE_REQUIRED"));
    }

    @Test
    void acceptsRepeatedLegacyRequestsWithoutRecommendationAttempt() throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenReturn(
                new RecommendationPlaylistCommandService.CreateResult(null, true, 44L, null));

        for (int index = 0; index < 2; index++) {
            mvc.perform(post("/api/recommendations/playlists")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"latitude\":37.5665,\"longitude\":126.9780}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recommendationId").value(44));
        }

        ArgumentCaptor<CreateRecommendationPlaylistRequest> captor =
                ArgumentCaptor.forClass(CreateRecommendationPlaylistRequest.class);
        verify(commandService, org.mockito.Mockito.times(2)).create(eq(7L), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CreateRecommendationPlaylistRequest::recommendationAttempt)
                .containsExactly(1, 1);
    }

    @Test
    void defaultsExplicitNullRecommendationAttemptToOne() throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenReturn(
                new RecommendationPlaylistCommandService.CreateResult(null, true, 44L, null));

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":null}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateRecommendationPlaylistRequest> captor =
                ArgumentCaptor.forClass(CreateRecommendationPlaylistRequest.class);
        verify(commandService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().recommendationAttempt()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3})
    void preservesExplicitRecommendationAttempt(int attempt) throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenReturn(
                new RecommendationPlaylistCommandService.CreateResult(null, true, 44L, null));

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":" + attempt + "}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateRecommendationPlaylistRequest> captor =
                ArgumentCaptor.forClass(CreateRecommendationPlaylistRequest.class);
        verify(commandService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().recommendationAttempt()).isEqualTo(attempt);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 4})
    void rejectsRecommendationAttemptOutsideSupportedRange(int attempt) throws Exception {
        authenticate(7L);

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":" + attempt + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    @Test
    void convertsWeatherFailureToBadGateway() throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenThrow(new WeatherApiException());

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":1}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("WEATHER_API_ERROR"));
    }

    @Test
    void convertsAiFailureToBadGateway() throws Exception {
        authenticate(7L);
        when(commandService.create(eq(7L), any())).thenThrow(new RecommendationAiException());

        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5665,\"longitude\":126.9780,\"recommendationAttempt\":1}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_ERROR"));
    }

    // 최대 50개 제한은 AI 호출 전에 공개 API 경계에서 검증한다.
    @Test
    void rejectsMoreThanFiftyExcludedTracks() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> "\"track-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5,\"longitude\":127,\"recommendationAttempt\":1,\"excludeTrackIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    // 빈 ID는 제외 필터로 전달하지 않고 입력 오류로 돌려준다.
    @Test
    void rejectsBlankExcludedTrackId() throws Exception {
        mvc.perform(post("/api/recommendations/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5,\"longitude\":127,\"recommendationAttempt\":1,\"excludeTrackIds\":[\" \"]}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(commandService);
    }

    // 인증 필터가 저장한 사용자 식별자를 컨트롤러 테스트에 설정한다.
    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
