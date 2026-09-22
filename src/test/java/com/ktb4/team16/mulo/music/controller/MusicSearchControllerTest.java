package com.ktb4.team16.mulo.music.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import com.ktb4.team16.mulo.music.domain.MusicProvider;
import com.ktb4.team16.mulo.music.domain.MusicSearchResult;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import com.ktb4.team16.mulo.music.service.MusicSearchService;
import java.time.Duration;
import java.util.List;
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
class MusicSearchControllerTest {
    @Mock MusicSearchService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MusicSearchController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));
    }

    @Test
    void returnsSpotifyAttributionFieldsForAuthenticatedUser() throws Exception {
        when(service.search(7L, "밤편지")).thenReturn(List.of(new MusicSearchResult(
                MusicProvider.SPOTIFY, "track-id", "밤편지", "아이유",
                "https://image.test/album.jpg",
                "https://open.spotify.com/track/track-id")));

        mvc.perform(get("/api/music/search").param("q", "밤편지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("음악 검색에 성공했습니다."))
                .andExpect(jsonPath("$.data[0].provider").value("SPOTIFY"))
                .andExpect(jsonPath("$.data[0].externalTrackId").value("track-id"))
                .andExpect(jsonPath("$.data[0].title").value("밤편지"))
                .andExpect(jsonPath("$.data[0].artistName").value("아이유"))
                .andExpect(jsonPath("$.data[0].albumImageUrl")
                        .value("https://image.test/album.jpg"))
                .andExpect(jsonPath("$.data[0].externalUrl")
                        .value("https://open.spotify.com/track/track-id"));
    }

    @Test
    void returnsRetryAfterWhenSearchIsRateLimited() throws Exception {
        when(service.search(7L, "밤편지"))
                .thenThrow(new MusicSearchRateLimitedException(Duration.ofSeconds(7)));

        mvc.perform(get("/api/music/search").param("q", "밤편지"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("MUSIC_SEARCH_RATE_LIMITED"));
    }

    @Test
    void returnsServiceUnavailableWithoutProviderDetails() throws Exception {
        when(service.search(7L, "밤편지"))
                .thenThrow(new MusicProviderUnavailableException(
                        new RuntimeException("provider body")));

        mvc.perform(get("/api/music/search").param("q", "밤편지"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MUSIC_PROVIDER_UNAVAILABLE"));
    }
}
