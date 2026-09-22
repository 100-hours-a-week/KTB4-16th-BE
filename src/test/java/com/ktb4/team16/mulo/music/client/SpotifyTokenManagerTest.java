package com.ktb4.team16.mulo.music.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SpotifyTokenManagerTest {
    @Test
    void reusesTokenUntilRefreshBoundary() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.test/api/token"))
                .andExpect(header("Authorization", "Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="))
                .andExpect(content().string("grant_type=client_credentials"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        SpotifyTokenManager manager = new SpotifyTokenManager(builder, properties(),
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));

        assertThat(manager.getToken()).isEqualTo("access-token");
        assertThat(manager.getToken()).isEqualTo("access-token");
        server.verify();
    }

    @Test
    void delayedUnauthorizedResponseCannotInvalidateNewerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://accounts.test/api/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-a\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://accounts.test/api/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-b\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        SpotifyTokenManager manager = new SpotifyTokenManager(builder, properties(),
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC));

        assertThat(manager.getToken()).isEqualTo("token-a");
        manager.invalidate("token-a");
        assertThat(manager.getToken()).isEqualTo("token-b");
        manager.invalidate("token-a");

        assertThat(manager.getToken()).isEqualTo("token-b");
        server.verify();
    }

    static SpotifyProperties properties() {
        return new SpotifyProperties(
                "client-id", "client-secret",
                URI.create("https://accounts.test"), URI.create("https://api.test"),
                Duration.ofSeconds(2), Duration.ofSeconds(3), 8, 15, 80);
    }
}
