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
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
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
    private static final String GROUP_ENDPOINT = "http://chat.test/internal/chat-rooms/group/join-or-create";
    private static final String NICKNAME_ENDPOINT = "http://chat.test/internal/users/17/nickname";
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

    @Test
    void sendsAuthenticatedNicknameUpdateAndParsesBareResponse() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(NICKNAME_ENDPOINT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Api-Key", "test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"nickname\":\"새닉네임\"}"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"userId\":17,\"updatedParticipantCount\":2}"));

        var response = fixture.client().updateParticipantNickname(17L, "새닉네임");

        assertThat(response.userId()).isEqualTo(17L);
        assertThat(response.updatedParticipantCount()).isEqualTo(2);
        fixture.server().verify();
    }

    @ParameterizedTest
    @MethodSource("nicknameHttpFailures")
    void classifiesNicknameEndpointHttpFailures(int status, FailureType expectedType) {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(NICKNAME_ENDPOINT))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("sensitive response body"));

        assertThatThrownBy(() -> fixture.client().updateParticipantNickname(17L, "새닉네임"))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(expectedType);
                    assertThat(exception.failureType().isRetryable())
                            .isEqualTo(expectedType == FailureType.SERVER_ERROR);
                    assertThat(exception.getMessage()).doesNotContain("sensitive response body");
                });
        fixture.server().verify();
    }

    @Test
    void classifiesNicknameEndpointTransportFailureAsCommunicationError() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(NICKNAME_ENDPOINT))
                .andRespond(request -> {
                    throw new IOException("sensitive transport detail");
                });

        assertThatThrownBy(() -> fixture.client().updateParticipantNickname(17L, "새닉네임"))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.COMMUNICATION_ERROR));
        fixture.server().verify();
    }

    @Test
    void classifiesNicknameEndpointTimeoutAsCommunicationError() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(NICKNAME_ENDPOINT))
                .andRespond(request -> {
                    throw new SocketTimeoutException("sensitive timeout detail");
                });

        assertThatThrownBy(() -> fixture.client().updateParticipantNickname(17L, "새닉네임"))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.COMMUNICATION_ERROR));
        fixture.server().verify();
    }

    @Test
    void rejectsNicknameResponseForDifferentUser() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(NICKNAME_ENDPOINT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"userId\":18,\"updatedParticipantCount\":2}"));

        assertThatThrownBy(() -> fixture.client().updateParticipantNickname(17L, "새닉네임"))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.INVALID_RESPONSE));
        fixture.server().verify();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = HttpStatus.class, names = {"CREATED", "OK"})
    void sendsAuthenticatedGroupJoinRequestAndAcceptsNewOrExistingAssignment(HttpStatus status) {
        Fixture fixture = fixture("test-api-key");
        ChatGroupJoinOrCreateRequest request = new ChatGroupJoinOrCreateRequest(
                LocalDate.of(2026, 10, 21), 41L, 17L, "jettie", "B");
        fixture.server().expect(requestTo(GROUP_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"groupDate":"2026-10-21","voteQuestionId":41,"userId":17,
                         "nickname":"jettie","voteChoice":"B"}
                        """))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"chatRoomId":30,"roomSequence":2,"newlyAssigned":true,
                                 "admittedParticipantCount":3,"activeParticipantCount":3,
                                 "expiresAt":"2026-10-22T00:00:00+09:00"}
                                """));

        var response = fixture.client().joinOrCreateGroup(request);

        assertThat(response.chatRoomId()).isEqualTo(30L);
        assertThat(response.roomSequence()).isEqualTo(2);
        assertThat(response.expiresAt()).isEqualTo(java.time.OffsetDateTime.parse("2026-10-22T00:00:00+09:00"));
        fixture.server().verify();
    }

    @Test
    void mapsExplicitlyLeftParticipantToNonRetryableReentryFailure() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(GROUP_ENDPOINT))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"CHAT_REENTRY_FORBIDDEN\"}"));

        assertThatThrownBy(() -> fixture.client().joinOrCreateGroup(
                new ChatGroupJoinOrCreateRequest(LocalDate.of(2026, 10, 21), 41L, 17L, "jettie", "A")))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(FailureType.GROUP_REENTRY_FORBIDDEN);
                    assertThat(exception.failureType().isRetryable()).isFalse();
                });
        fixture.server().verify();
    }

    @Test
    void rejectsGroupResponseOutsideCapacityContract() {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(GROUP_ENDPOINT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"chatRoomId":30,"roomSequence":2,"newlyAssigned":true,
                                 "admittedParticipantCount":31,"activeParticipantCount":31,
                                 "expiresAt":"2026-10-22T00:00:00+09:00"}
                                """));

        assertThatThrownBy(() -> fixture.client().joinOrCreateGroup(
                new ChatGroupJoinOrCreateRequest(LocalDate.of(2026, 10, 21), 41L, 17L, "jettie", "A")))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(FailureType.INVALID_RESPONSE));
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

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> nicknameHttpFailures() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(400, FailureType.CLIENT_ERROR),
                org.junit.jupiter.params.provider.Arguments.of(401, FailureType.AUTHENTICATION_ERROR),
                org.junit.jupiter.params.provider.Arguments.of(403, FailureType.AUTHENTICATION_ERROR),
                org.junit.jupiter.params.provider.Arguments.of(500, FailureType.SERVER_ERROR));
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
