package com.ktb4.team16.mulo.match.client;

import com.ktb4.team16.mulo.match.client.ChatServerClientException.FailureType;
import com.ktb4.team16.mulo.match.config.ChatServerProperties;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.ChatRoomCreateResponse;
import com.ktb4.team16.mulo.match.dto.response.ChatGroupJoinOrCreateResponse;
import java.net.http.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ChatRoomClient {
    private static final String CREATE_ENDPOINT = "/internal/chat-rooms";
    private static final String GROUP_JOIN_ENDPOINT = "/internal/chat-rooms/group/join-or-create";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final ChatServerProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChatRoomClient(ChatServerProperties properties) {
        this(configuredBuilder(RestClient.builder(), properties), properties, new ObjectMapper());
    }

    ChatRoomClient(RestClient.Builder builder, ChatServerProperties properties) {
        this(builder, properties, new ObjectMapper());
    }

    ChatRoomClient(RestClient.Builder builder, ChatServerProperties properties, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.baseUrl().toString()).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ChatRoomCreateResponse createChatRoom(ChatRoomCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("채팅방 생성 요청이 필요합니다.");
        }
        if (!properties.hasInternalApiKey()) {
            throw new ChatServerClientException(FailureType.CONFIGURATION_ERROR, null);
        }

        try {
            var response = restClient.post()
                    .uri(CREATE_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_API_KEY_HEADER, properties.internalApiKey())
                    .body(request)
                    .retrieve()
                    .toEntity(ChatRoomCreateResponse.class);

            int status = response.getStatusCode().value();
            ChatRoomCreateResponse body = response.getBody();
            if ((status != 200 && status != 201)
                    || body == null || body.chatRoomId() == null || body.chatRoomId() <= 0) {
                throw new ChatServerClientException(FailureType.INVALID_RESPONSE, status);
            }
            return body;
        } catch (ChatServerClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw classifyHttpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new ChatServerClientException(FailureType.COMMUNICATION_ERROR, null);
        } catch (RestClientException exception) {
            throw new ChatServerClientException(FailureType.INVALID_RESPONSE, null);
        }
    }

    /** 동일 사용자·일자·질문의 재호출은 Chat Server의 멱등 배정 결과를 회수한다. */
    public ChatGroupJoinOrCreateResponse joinOrCreateGroup(ChatGroupJoinOrCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("그룹 채팅방 입장 요청이 필요합니다.");
        }
        if (!properties.hasInternalApiKey()) {
            throw new ChatServerClientException(FailureType.CONFIGURATION_ERROR, null);
        }

        try {
            var response = restClient.post()
                    .uri(GROUP_JOIN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_API_KEY_HEADER, properties.internalApiKey())
                    .body(request)
                    .retrieve()
                    .toEntity(ChatGroupJoinOrCreateResponse.class);

            int status = response.getStatusCode().value();
            ChatGroupJoinOrCreateResponse body = response.getBody();
            if ((status != 200 && status != 201) || !isValidGroupResponse(body)) {
                throw new ChatServerClientException(FailureType.INVALID_RESPONSE, status);
            }
            return body;
        } catch (ChatServerClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw classifyHttpFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new ChatServerClientException(FailureType.COMMUNICATION_ERROR, null);
        } catch (RestClientException exception) {
            throw new ChatServerClientException(FailureType.INVALID_RESPONSE, null);
        }
    }

    private boolean isValidGroupResponse(ChatGroupJoinOrCreateResponse response) {
        return response != null
                && response.chatRoomId() != null && response.chatRoomId() > 0
                && response.roomSequence() > 0
                && response.admittedParticipantCount() >= 1 && response.admittedParticipantCount() <= 30
                && response.activeParticipantCount() >= 0
                && response.activeParticipantCount() <= response.admittedParticipantCount()
                && response.expiresAt() != null;
    }

    private ChatServerClientException classifyHttpFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String errorCode = extractErrorCode(exception);
        if ("CHAT_REENTRY_FORBIDDEN".equals(errorCode)) {
            return new ChatServerClientException(FailureType.GROUP_REENTRY_FORBIDDEN, status);
        }
        if ("INTERNAL_AUTH_UNAVAILABLE".equals(errorCode)) {
            return new ChatServerClientException(FailureType.CONFIGURATION_ERROR, status);
        }
        if (status == 401 || status == 403) {
            return new ChatServerClientException(FailureType.AUTHENTICATION_ERROR, status);
        }
        if (status == 409) {
            if ("CHAT_ROOM_EXPIRED".equals(errorCode)) {
                return new ChatServerClientException(FailureType.EXPIRED_REQUEST, status);
            }
            return new ChatServerClientException(FailureType.REQUEST_CONFLICT, status);
        }
        if (exception.getStatusCode().is4xxClientError()) {
            return new ChatServerClientException(FailureType.CLIENT_ERROR, status);
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return new ChatServerClientException(FailureType.SERVER_ERROR, status);
        }
        return new ChatServerClientException(FailureType.INVALID_RESPONSE, status);
    }

    private String extractErrorCode(RestClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            JsonNode code = body == null ? null : body.get("code");
            return code != null && code.isTextual() ? code.asText() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static RestClient.Builder configuredBuilder(RestClient.Builder builder,
            ChatServerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory);
    }
}
