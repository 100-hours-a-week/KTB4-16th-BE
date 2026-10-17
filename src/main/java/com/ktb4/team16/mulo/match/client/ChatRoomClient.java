package com.ktb4.team16.mulo.match.client;

import com.ktb4.team16.mulo.match.client.ChatServerClientException.FailureType;
import com.ktb4.team16.mulo.match.config.ChatServerProperties;
import com.ktb4.team16.mulo.match.dto.request.ChatRoomCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.ChatRoomCreateResponse;
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

    private ChatServerClientException classifyHttpFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String errorCode = extractErrorCode(exception);
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
