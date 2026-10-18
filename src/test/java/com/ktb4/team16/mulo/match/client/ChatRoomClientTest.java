package com.ktb4.team16.mulo.match.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ktb4.team16.mulo.match.client.ChatServerClientException.FailureType;
import com.ktb4.team16.mulo.match.config.ChatServerProperties;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest.Participant;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ChatRoomClientTest {
    private static final String ENDPOINT = "http://chat.test/internal/chat-rooms";
    private static final ChatRoomCreateRequest REQUEST = new ChatRoomCreateRequest(50L,
            OffsetDateTime.parse("2026-10-18T00:00:00+09:00"), java.util.List.of(
                    new Participant(17L, "jettie"), new Participant(28L, "lulu")));

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = HttpStatus.class, names = {"CREATED", "OK"})
    void sendsRequestAndAcceptsCreatedOrIdempotentResponse(HttpStatus status) {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"matchId":50,"expiresAt":"2026-10-18T00:00:00+09:00","participants":[
                          {"userId":17,"nickname":"jettie"},
                          {"userId":28,"nickname":"lulu"}
                        ]}
                        """))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"chatRoomId\":30}"));

        assertThat(fixture.client().createChatRoom(REQUEST).chatRoomId()).isEqualTo(30L);

        fixture.server().verify();
    }

    @ParameterizedTest
    @MethodSource("httpFailures")
    void classifiesHttpFailures(int status, FailureType expectedType) {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("sensitive response body"));

        assertThatThrownBy(() -> fixture.client().createChatRoom(REQUEST))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(expectedType);
                    assertThat(exception.httpStatus()).isEqualTo(status);
                    assertThat(exception.getMessage()).doesNotContain("sensitive response body");
                });

        fixture.server().verify();
    }

    @Test
    void classifiesTransportFailureAsCommunicationError() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(request -> {
                    throw new IOException("sensitive transport detail");
                });

        assertThatThrownBy(() -> fixture.client().createChatRoom(REQUEST))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(FailureType.COMMUNICATION_ERROR);
                    assertThat(exception.httpStatus()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("sensitive transport detail");
                });

        fixture.server().verify();
    }

    @Test
    void rejectsMalformedSuccessResponse() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        assertThatThrownBy(() -> fixture.client().createChatRoom(REQUEST))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(FailureType.INVALID_RESPONSE);
                    assertThat(exception.httpStatus()).isEqualTo(201);
                });

        fixture.server().verify();
    }

    @Test
    void distinguishesExpiredCreationFromExistingRoomConflict() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"CHAT_ROOM_EXPIRED\",\"message\":\"expired\"}"));

        assertThatThrownBy(() -> fixture.client().createChatRoom(REQUEST))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.EXPIRED_REQUEST));
        fixture.server().verify();
    }

    @Test
    void classifiesChatServerInternalApiKeyConfigurationAsNonRetryableConfigurationFailure() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INTERNAL_AUTH_UNAVAILABLE\"}"));

        assertThatThrownBy(() -> fixture.client().createChatRoom(REQUEST))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.CONFIGURATION_ERROR));
        fixture.server().verify();
    }

    @Test
    void doesNotSendRequestWhenInternalApiKeyIsMissing() {
        Fixture fixture = fixture("");

        assertThatThrownBy(() -> fixture.client().createChatRoom(REQUEST))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.CONFIGURATION_ERROR));

        fixture.server().verify();
    }

    @Test
    void requestDtoRejectsInvalidParticipantsBeforeHttpCall() {
        assertThatThrownBy(() -> new ChatRoomCreateRequest(50L,
                OffsetDateTime.parse("2026-10-18T00:00:00+09:00"), java.util.List.of(
                        new Participant(17L, "jettie"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Participant(17L, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Participant(null, "lulu"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> httpFailures() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(400, FailureType.CLIENT_ERROR),
                org.junit.jupiter.params.provider.Arguments.of(401, FailureType.AUTHENTICATION_ERROR),
                org.junit.jupiter.params.provider.Arguments.of(409, FailureType.REQUEST_CONFLICT),
                org.junit.jupiter.params.provider.Arguments.of(500, FailureType.SERVER_ERROR),
                org.junit.jupiter.params.provider.Arguments.of(503, FailureType.SERVER_ERROR));
    }

    private Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatServerProperties properties = new ChatServerProperties(
                URI.create("http://chat.test"), apiKey, Duration.ofSeconds(2), Duration.ofSeconds(3));
        return new Fixture(server, new ChatRoomClient(builder, properties));
    }

    private record Fixture(MockRestServiceServer server, ChatRoomClient client) { }
}
