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
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ChatRoomClientTest {
    private static final String GROUP_ENDPOINT = "http://chat.test/internal/chat-rooms/group/join-or-create";
    private static final String NICKNAME_ENDPOINT = "http://chat.test/internal/users/17/nickname";

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

    /** 닉네임 경로에서 내부 인증 설정 오류와 만료·일반 충돌의 공통 분류 계약을 검증한다. */
    @ParameterizedTest
    @MethodSource("configurationAndConflictHttpFailures")
    void classifiesNicknameEndpointConfigurationAndConflictFailures(
            int status, String responseBody, FailureType expectedType, boolean retryable) {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(NICKNAME_ENDPOINT))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        assertThatThrownBy(() -> fixture.client().updateParticipantNickname(17L, "새닉네임"))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(expectedType);
                    assertThat(exception.httpStatus()).isEqualTo(status);
                    assertThat(exception.failureType().isRetryable()).isEqualTo(retryable);
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

    /** 그룹 경로에서도 내부 인증 설정 오류와 만료·일반 충돌이 구별되는지 검증한다. */
    @ParameterizedTest
    @MethodSource("configurationAndConflictHttpFailures")
    void classifiesGroupEndpointConfigurationAndConflictFailures(
            int status, String responseBody, FailureType expectedType, boolean retryable) {
        Fixture fixture = fixture("test-api-key");
        fixture.server().expect(requestTo(GROUP_ENDPOINT))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        assertThatThrownBy(() -> fixture.client().joinOrCreateGroup(
                new ChatGroupJoinOrCreateRequest(LocalDate.of(2026, 10, 21), 41L, 17L, "jettie", "A")))
                .isInstanceOfSatisfying(ChatServerClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(expectedType);
                    assertThat(exception.httpStatus()).isEqualTo(status);
                    assertThat(exception.failureType().isRetryable()).isEqualTo(retryable);
                    assertThat(exception.getMessage()).doesNotContain("sensitive response body");
                });
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

    /** 오류 코드 우선 분기와 일반 HTTP 상태 분기를 구별할 공통 회귀 입력을 제공한다. */
    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
            configurationAndConflictHttpFailures() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(503,
                        "{\"code\":\"INTERNAL_AUTH_UNAVAILABLE\",\"message\":\"sensitive response body\"}",
                        FailureType.CONFIGURATION_ERROR, false),
                org.junit.jupiter.params.provider.Arguments.of(503,
                        "{\"message\":\"sensitive response body\"}", FailureType.SERVER_ERROR, true),
                org.junit.jupiter.params.provider.Arguments.of(409,
                        "{\"code\":\"CHAT_ROOM_EXPIRED\",\"message\":\"sensitive response body\"}",
                        FailureType.EXPIRED_REQUEST, false),
                org.junit.jupiter.params.provider.Arguments.of(409,
                        "{\"code\":\"OTHER_CONFLICT\",\"message\":\"sensitive response body\"}",
                        FailureType.REQUEST_CONFLICT, false),
                org.junit.jupiter.params.provider.Arguments.of(409,
                        "sensitive response body", FailureType.REQUEST_CONFLICT, false));
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
