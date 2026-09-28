package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"imageUrl\":\"https://signed.example/photo\"}"))
                .andRespond(withSuccess("""
                        {"tracks":[{"title":"Title","artistName":"Artist","externalTrackId":"id","spotifyUri":"spotify:track:id","albumImageUrl":"album","externalUrl":"external"}],"degraded":false}
                        """, MediaType.APPLICATION_JSON));

        var response = client.recommend("https://signed.example/photo");

        assertThat(response.tracks()).hasSize(1);
        assertThat(response.tracks().getFirst().externalTrackId()).isEqualTo("id");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"imageUrl\"", "null", "\"https://signed.example/private?secret=value\""})
    @ExtendWith(OutputCaptureExtension.class)
    void logsOnlySafeBadRequestFieldsAndKeepsExceptionMapping(String field, CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create("http://ai.test"), "test-token",
                Duration.ofSeconds(1), Duration.ofSeconds(1), false);
        PhotoRecommendationAiClient client = new PhotoRecommendationAiClient(builder, properties);
        server.expect(requestTo("http://ai.test/api/photo-recommend"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INVALID_INPUT\",\"message\":\"test-token https://signed.example/private\","
                                + "\"field\":" + field + "}"));

        assertThatThrownBy(() -> client.recommend("https://signed.example/photo"))
                .isInstanceOf(PhotoRecommendationAiException.class)
                .hasCauseInstanceOf(org.springframework.web.client.RestClientResponseException.class);

        String expectedField = "null".equals(field) ? "null"
                : "\"imageUrl\"".equals(field) ? "imageUrl" : "OTHER";
        assertThat(output.getAll()).contains("HTTP 400: code=INVALID_INPUT, field=" + expectedField)
                .doesNotContain("test-token", "https://signed.example", "secret=value");
        server.verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void keepsExceptionMappingForNonJsonBadRequest(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PhotoRecommendationAiClient client = new PhotoRecommendationAiClient(builder,
                new AiProperties(URI.create("http://ai.test"), "test-token",
                        Duration.ofSeconds(1), Duration.ofSeconds(1), false));
        server.expect(requestTo("http://ai.test/api/photo-recommend"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body("test-token https://signed.example/private"));

        assertThatThrownBy(() -> client.recommend("https://signed.example/photo"))
                .isInstanceOf(PhotoRecommendationAiException.class);
        assertThat(output.getAll()).contains("HTTP 400: response=UNPARSEABLE")
                .doesNotContain("test-token", "https://signed.example");
        server.verify();
    }
}
