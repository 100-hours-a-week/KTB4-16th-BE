package com.ktb4.team16.mulo.record.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RecordEmbeddingAiClientTest {
    private static final String ENDPOINT = "http://ai.test/api/embeddings/generate";
    private static final String DELETE_ENDPOINT = "http://ai.test/api/embeddings/1024";
    private static final EmbeddingGenerateRequest REQUEST = new EmbeddingGenerateRequest(
            1024L, 7L, "https://signed.example/photo",
            new EmbeddingGenerateRequest.Track("밤편지", "아이유", "track-123"),
            null, "2026-09-30T19:40:00+09:00");

    @Test
    void sendsInternalTokenAndJsonAndAcceptsQueuedResponse() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-dummy-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"recordId":1024,"userId":7,"photoUrl":"https://signed.example/photo",
                         "track":{"title":"밤편지","artistName":"아이유",
                         "externalTrackId":"track-123"},"comment":null,
                         "createdAt":"2026-09-30T19:40:00+09:00"}
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"QUEUED\",\"jobId\":\"emb_381\"}"));

        fixture.client().generate(REQUEST);

        fixture.server().verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "BAD_GATEWAY"})
    void wrapsAiHttpErrorsWithoutKeepingResponseBody(HttpStatus status) {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(status).body("sensitive AI body"));

        assertThatThrownBy(() -> fixture.client().generate(REQUEST))
                .isInstanceOf(RecordEmbeddingAiException.class)
                .hasMessageNotContaining("sensitive AI body");

        fixture.server().verify();
    }

    @Test
    void rejectsNon202OrMalformedQueuedResponse() {
        Fixture non202 = fixture();
        non202.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"QUEUED\",\"jobId\":\"emb_381\"}"));
        assertThatThrownBy(() -> non202.client().generate(REQUEST))
                .isInstanceOf(RecordEmbeddingAiException.class);

        Fixture malformed = fixture();
        malformed.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{not-json"));
        assertThatThrownBy(() -> malformed.client().generate(REQUEST))
                .isInstanceOf(RecordEmbeddingAiException.class);
    }

    @Test
    void wrapsTransportFailureWithoutExposingTransportDetails() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(request -> {
                    throw new IOException("sensitive transport detail");
                });

        assertThatThrownBy(() -> fixture.client().generate(REQUEST))
                .isInstanceOf(RecordEmbeddingAiException.class)
                .hasMessageNotContaining("sensitive transport detail");

        fixture.server().verify();
    }

    @Test
    void deletesEmbeddingWithTokenAndNoBodyAndAcceptsNoContent() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(DELETE_ENDPOINT))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", "test-dummy-token"))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        fixture.client().delete(1024L);

        fixture.server().verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "BAD_GATEWAY"})
    void wrapsDeleteAiHttpErrorsWithoutKeepingResponseBody(HttpStatus status) {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(DELETE_ENDPOINT))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(status).body("sensitive AI body"));

        assertThatThrownBy(() -> fixture.client().delete(1024L))
                .isInstanceOfSatisfying(RecordEmbeddingAiException.class, exception -> {
                    assertThat(exception.httpStatus()).isEqualTo(status.value());
                    assertThat(exception.getMessage()).doesNotContain("sensitive AI body");
                });

        fixture.server().verify();
    }

    @Test
    void wrapsDeleteTransportFailureWithoutExposingTransportDetails() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(DELETE_ENDPOINT))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(request -> {
                    throw new IOException("sensitive transport detail");
                });

        assertThatThrownBy(() -> fixture.client().delete(1024L))
                .isInstanceOf(RecordEmbeddingAiException.class)
                .hasMessageNotContaining("sensitive transport detail");

        fixture.server().verify();
    }

    @Test
    void doesNotRetryDeleteAfterHttpFailure() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(DELETE_ENDPOINT))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> fixture.client().delete(1024L))
                .isInstanceOfSatisfying(RecordEmbeddingAiException.class,
                        exception -> assertThat(exception.httpStatus()).isEqualTo(503));

        fixture.server().verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(URI.create("http://ai.test"),
                "test-dummy-token", Duration.ofSeconds(1), Duration.ofSeconds(1), false);
        return new Fixture(server, new RecordEmbeddingAiClient(builder, properties));
    }

    private record Fixture(MockRestServiceServer server, RecordEmbeddingAiClient client) { }
}
