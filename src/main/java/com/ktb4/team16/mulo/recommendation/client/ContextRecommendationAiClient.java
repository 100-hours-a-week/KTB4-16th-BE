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
public class ContextRecommendationAiClient implements RecommendationAiClient {
    private static final int RECOMMENDATION_LIMIT = 10;

    private final RestClient restClient;
    private final AiProperties properties;

    @Autowired
    public ContextRecommendationAiClient(AiProperties properties) {
        this(configuredBuilder(RestClient.builder(), properties), properties);
    }

    ContextRecommendationAiClient(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    // AI Gateway의 상황 맞춤 추천 API를 호출하고 응답을 내부 추천 결과로 변환한다.
    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        try {
            ContextRecommendationAiResponse response = restClient.post()
                    .uri(properties.baseUrl() + "/api/context-recommend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", properties.internalToken())
                    .body(new ContextRecommendationAiRequest(context.userId(),
                            new Weather(context.weatherCondition().name(), context.temperature()),
                            context.requestedAt(), RECOMMENDATION_LIMIT))
                    .retrieve()
                    .body(ContextRecommendationAiResponse.class);
            if (response == null || response.tracks() == null) {
                throw new RecommendationAiException();
            }
            return new RecommendationResult(response.tracks().stream().map(this::toRecommendedTrack).toList(),
                    response.degraded());
        } catch (RecommendationAiException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new RecommendationAiException(exception);
        }
    }

    // AI 응답 곡을 DB 저장에 필요한 필수 필드까지 검증하여 내부 모델로 변환한다.
    private RecommendedTrack toRecommendedTrack(ContextRecommendationAiResponse.Track track) {
        if (track == null || isBlank(track.externalTrackId()) || isBlank(track.title())
                || isBlank(track.artistName()) || isBlank(track.albumImageUrl()) || isBlank(track.externalUrl())) {
            throw new RecommendationAiException();
        }
        return new RecommendedTrack(track.externalTrackId(), track.title(), track.artistName(),
                track.albumImageUrl(), track.externalUrl());
    }

    // AI 통신의 연결·읽기 제한 시간을 RestClient 요청 팩토리에 적용한다.
    private static RestClient.Builder configuredBuilder(RestClient.Builder builder, AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory);
    }

    // AI Gateway 요청 JSON 구조를 명세의 camelCase 필드명으로 고정한다.
    private record ContextRecommendationAiRequest(Long userId, Weather weather,
            java.time.OffsetDateTime localTime, int limit) { }

    // 요청의 날씨 enum 이름과 섭씨 온도를 중첩 JSON 객체로 전달한다.
    private record Weather(String condition, java.math.BigDecimal temperature) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContextRecommendationAiResponse(List<Track> tracks, boolean degraded) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Track(String title, String artistName, String externalTrackId,
                String spotifyUri, String albumImageUrl, String externalUrl) { }
    }

    // 공백만 있는 필수 응답 필드를 저장 전에 거부한다.
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
