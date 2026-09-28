package com.ktb4.team16.mulo.recommendation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.http.HttpClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class PhotoRecommendationAiClient implements PhotoRecommendationAiGateway {
    private final RestClient restClient;
    private final AiProperties properties;

    @Autowired
    public PhotoRecommendationAiClient(AiProperties properties) {
        this(configuredBuilder(RestClient.builder(), properties), properties);
    }

    PhotoRecommendationAiClient(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public PhotoRecommendationAiResponse recommend(String imageUrl) {
        try {
            PhotoRecommendationAiResponse response = restClient.post()
                    .uri(properties.baseUrl() + "/api/photo-recommend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", properties.internalToken())
                    .body(new PhotoRecommendationAiRequest(imageUrl))
                    .retrieve()
                    .body(PhotoRecommendationAiResponse.class);
            if (response == null || response.tracks() == null) {
                throw new PhotoRecommendationAiException();
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400) {
                logBadRequestDiagnostic(exception);
            }
            throw new PhotoRecommendationAiException(exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new PhotoRecommendationAiException(exception);
        }
    }

    private void logBadRequestDiagnostic(RestClientResponseException exception) {
        try {
            AiErrorResponse error = exception.getResponseBodyAs(AiErrorResponse.class);
            if (error == null) {
                log.warn("Photo recommendation AI HTTP 400: response=UNPARSEABLE");
                return;
            }
            String code = "INVALID_INPUT".equals(error.code()) ? "INVALID_INPUT" : "OTHER";
            String field = error.field() == null ? "null"
                    : "imageUrl".equals(error.field()) ? "imageUrl" : "OTHER";
            log.warn("Photo recommendation AI HTTP 400: code={}, field={}", code, field);
        } catch (RuntimeException diagnosticFailure) {
            // Never log raw response bodies or exception details containing credentials or URLs.
            log.warn("Photo recommendation AI HTTP 400: response=UNPARSEABLE");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiErrorResponse(String code, String field) {
    }

    private static RestClient.Builder configuredBuilder(RestClient.Builder builder,
            AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory);
    }

    private record PhotoRecommendationAiRequest(String imageUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoRecommendationAiResponse(List<Track> tracks, boolean degraded) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Track(String title, String artistName, String externalTrackId,
                String spotifyUri, String albumImageUrl, String externalUrl) {
        }
    }
}
