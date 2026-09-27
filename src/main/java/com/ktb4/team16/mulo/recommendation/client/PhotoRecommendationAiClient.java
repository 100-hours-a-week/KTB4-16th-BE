package com.ktb4.team16.mulo.recommendation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new PhotoRecommendationAiException(exception);
        }
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
