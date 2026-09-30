package com.ktb4.team16.mulo.record.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RecordEmbeddingAiClient {
    private static final String ENDPOINT = "/api/embeddings/generate";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final AiProperties properties;

    @Autowired
    public RecordEmbeddingAiClient(AiProperties properties) {
        this(configuredBuilder(RestClient.builder(), properties), properties);
    }

    RecordEmbeddingAiClient(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public void generate(EmbeddingGenerateRequest request) {
        try {
            var response = restClient.post()
                    .uri(properties.baseUrl() + ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(INTERNAL_TOKEN_HEADER, properties.internalToken())
                    .body(request)
                    .retrieve()
                    .toEntity(QueuedResponse.class);

            QueuedResponse body = response.getBody();
            if (response.getStatusCode().value() != 202
                    || body == null
                    || !"QUEUED".equals(body.status())
                    || body.jobId() == null
                    || body.jobId().isBlank()) {
                throw new RecordEmbeddingAiException(response.getStatusCode().value(),
                        RecordEmbeddingAiException.FailureType.INVALID_RESPONSE);
            }
        } catch (RecordEmbeddingAiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new RecordEmbeddingAiException(exception.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new RecordEmbeddingAiException();
        }
    }

    private static RestClient.Builder configuredBuilder(RestClient.Builder builder,
            AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QueuedResponse(String status, String jobId) { }
}
