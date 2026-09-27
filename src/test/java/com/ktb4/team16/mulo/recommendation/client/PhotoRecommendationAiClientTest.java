package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PhotoRecommendationAiClientTest {
    @Test
    void sendsSignedUrlAndInternalTokenAndMapsResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create("http://ai.test"), "test-token",
                Duration.ofSeconds(1), Duration.ofSeconds(1), false);
        PhotoRecommendationAiClient client = new PhotoRecommendationAiClient(builder, properties);
        server.expect(requestTo("http://ai.test/api/photo-recommend"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(content().json("{\"imageUrl\":\"https://signed.example/photo\"}"))
                .andRespond(withSuccess("""
                        {"tracks":[{"title":"Title","artistName":"Artist","externalTrackId":"id","spotifyUri":"spotify:track:id","albumImageUrl":"album","externalUrl":"external"}],"degraded":false}
                        """, MediaType.APPLICATION_JSON));

        var response = client.recommend("https://signed.example/photo");

        assertThat(response.tracks()).hasSize(1);
        assertThat(response.tracks().getFirst().externalTrackId()).isEqualTo("id");
        server.verify();
    }
}
