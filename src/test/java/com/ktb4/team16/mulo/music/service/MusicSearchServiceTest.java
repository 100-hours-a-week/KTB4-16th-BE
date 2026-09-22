package com.ktb4.team16.mulo.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.music.client.MusicCatalogClient;
import com.ktb4.team16.mulo.music.client.SpotifyApiException;
import com.ktb4.team16.mulo.music.domain.MusicProvider;
import com.ktb4.team16.mulo.music.domain.MusicSearchResult;
import com.ktb4.team16.mulo.music.exception.MusicProviderUnavailableException;
import com.ktb4.team16.mulo.music.exception.MusicSearchInputException;
import com.ktb4.team16.mulo.music.exception.MusicSearchRateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusicSearchServiceTest {
    private MusicCatalogClient catalogClient;
    private MusicSearchService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        catalogClient = mock(MusicCatalogClient.class);
        service = new MusicSearchService(catalogClient,
                new UserMusicSearchRateLimiter(clock, 8, 15));
    }

    @Test
    void normalizesWhitespaceAndDoesNotCacheResults() {
        MusicSearchResult result = result();
        when(catalogClient.searchTracks("아이유 밤편지")).thenReturn(List.of(result));

        assertThat(service.search(1L, "  아이유   밤편지  ")).containsExactly(result);
        assertThat(service.search(1L, "아이유 밤편지")).containsExactly(result);

        verify(catalogClient, times(2)).searchTracks("아이유 밤편지");
    }

    @Test
    void rejectsBlankOneCharacterAndOverTwoHundredFiftyFiveCharacters() {
        assertThatThrownBy(() -> service.search(1L, " "))
                .isInstanceOf(MusicSearchInputException.class);
        assertThatThrownBy(() -> service.search(1L, "가"))
                .isInstanceOf(MusicSearchInputException.class);
        assertThatThrownBy(() -> service.search(1L, "가".repeat(256)))
                .isInstanceOf(MusicSearchInputException.class);
    }

    @Test
    void convertsSpotifyRateLimitToPublicRateLimitException() {
        when(catalogClient.searchTracks("밤편지"))
                .thenThrow(SpotifyApiException.rateLimited(Duration.ofSeconds(7)));

        assertThatThrownBy(() -> service.search(1L, "밤편지"))
                .isInstanceOf(MusicSearchRateLimitedException.class);
        verify(catalogClient).searchTracks("밤편지");
    }

    @Test
    void convertsProviderFailureToStableUnavailableException() {
        when(catalogClient.searchTracks("밤편지"))
                .thenThrow(SpotifyApiException.unavailable(new RuntimeException("upstream")));

        assertThatThrownBy(() -> service.search(1L, "밤편지"))
                .isInstanceOf(MusicProviderUnavailableException.class);
    }

    private MusicSearchResult result() {
        return new MusicSearchResult(MusicProvider.SPOTIFY, "track-id", "밤편지", "아이유",
                "https://image.test/album.jpg", "https://open.spotify.com/track/track-id");
    }
}
