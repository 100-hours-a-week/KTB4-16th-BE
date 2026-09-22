package com.ktb4.team16.mulo.music.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.music.domain.MusicProvider;
import com.ktb4.team16.mulo.music.service.SpotifySearchRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class SpotifyMusicCatalogClientTest {
    private MockRestServiceServer server;
    private SpotifyTokenManager tokenManager;
    private SpotifySearchRateLimiter rateLimiter;
    private SpotifyMusicCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokenManager = mock(SpotifyTokenManager.class);
        rateLimiter = mock(SpotifySearchRateLimiter.class);
        when(tokenManager.getToken()).thenReturn("token");
        client = new SpotifyMusicCatalogClient(builder,
                SpotifyTokenManagerTest.properties(), tokenManager,
                rateLimiter, new ObjectMapper());
    }

    @Test
    void searchesKoreanTracksAndMapsAttributionFields() {
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/v1/search?"),
                        org.hamcrest.Matchers.containsString("q=%EB%B0%A4%ED%8E%B8%EC%A7%80"),
                        org.hamcrest.Matchers.containsString("type=track"),
                        org.hamcrest.Matchers.containsString("market=KR"),
                        org.hamcrest.Matchers.containsString("limit=10"))))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        var results = client.searchTracks("밤편지");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().provider()).isEqualTo(MusicProvider.SPOTIFY);
        assertThat(results.getFirst().externalTrackId()).isEqualTo("1Bb6jVrsg8cXxMCBxIWJUn");
        assertThat(results.getFirst().title()).isEqualTo("밤편지");
        assertThat(results.getFirst().artistName()).isEqualTo("아이유");
        assertThat(results.getFirst().albumImageUrl()).isEqualTo("https://image.test/album.jpg");
        assertThat(results.getFirst().externalUrl())
                .isEqualTo("https://open.spotify.com/track/1Bb6jVrsg8cXxMCBxIWJUn");
        server.verify();
    }

    @Test
    void preservesRetryAfterFromRateLimit() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "7"));

        assertThatThrownBy(() -> client.searchTracks("밤편지"))
                .isInstanceOfSatisfying(SpotifyApiException.class,
                        exception -> {
                            assertThat(exception.kind())
                                    .isEqualTo(SpotifyApiException.Kind.RATE_LIMITED);
                            assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(7));
                        });
        verify(rateLimiter).block(Duration.ofSeconds(7));
    }

    @Test
    void refreshesTokenAndRetriesOnlyOnceAfterUnauthorized() {
        when(tokenManager.getToken()).thenReturn("expired-token", "new-token");
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new-token"))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        assertThat(client.searchTracks("밤편지")).hasSize(1);
        verify(tokenManager).invalidate("expired-token");
        verify(rateLimiter, org.mockito.Mockito.times(2)).acquire();
        server.verify();
    }

    @Test
    void quotaExceededIsUnavailableInsteadOfRetryableRateLimit() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":429,\"message\":\"quota\","
                                + "\"reason\":\"QUOTA_EXCEEDED\"}}"));

        assertThatThrownBy(() -> client.searchTracks("밤편지"))
                .isInstanceOfSatisfying(SpotifyApiException.class,
                        exception -> assertThat(exception.kind())
                                .isEqualTo(SpotifyApiException.Kind.UNAVAILABLE));
    }

    @Test
    void skipsNullAndIncompleteTrackItems() {
        server.expect(requestTo(org.hamcrest.Matchers.any(String.class)))
                .andRespond(withSuccess(
                        "{\"tracks\":{\"items\":[null,{\"id\":\"id\","
                                + "\"name\":\"title\",\"artists\":[null],"
                                + "\"album\":{\"images\":[null]},"
                                + "\"external_urls\":{\"spotify\":\"url\"}}]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.searchTracks("밤편지")).isEmpty();
    }

    private String successBody() {
        return """
                {"tracks":{"items":[{
                  "id":"1Bb6jVrsg8cXxMCBxIWJUn",
                  "name":"밤편지",
                  "artists":[{"name":"아이유"}],
                  "album":{"images":[{"url":"https://image.test/album.jpg"}]},
                  "external_urls":{"spotify":"https://open.spotify.com/track/1Bb6jVrsg8cXxMCBxIWJUn"}
                }]}}
                """;
    }
}
