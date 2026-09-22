package com.ktb4.team16.mulo.music.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import com.ktb4.team16.mulo.music.domain.MusicProvider;
import com.ktb4.team16.mulo.music.domain.MusicSearchResult;
import com.ktb4.team16.mulo.music.service.SpotifySearchRateLimiter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SpotifyMusicCatalogClient implements MusicCatalogClient {
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);

    private final RestClient restClient;
    private final SpotifyProperties properties;
    private final SpotifyTokenManager tokenManager;
    private final SpotifySearchRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    // 운영용 HTTP timeout을 적용한 Spotify 검색 어댑터를 생성한다.
    @Autowired
    public SpotifyMusicCatalogClient(SpotifyProperties properties,
            SpotifyTokenManager tokenManager, SpotifySearchRateLimiter rateLimiter,
            ObjectMapper objectMapper) {
        this(configuredBuilder(RestClient.builder(), properties), properties, tokenManager,
                rateLimiter, objectMapper);
    }

    // 테스트가 제어하는 RestClient로 Spotify 검색 어댑터를 생성한다.
    SpotifyMusicCatalogClient(RestClient.Builder builder,
            SpotifyProperties properties, SpotifyTokenManager tokenManager,
            SpotifySearchRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    // 트랙을 검색하고 401이면 공급자 토큰만 한 번 갱신해 재시도한다.
    public List<MusicSearchResult> searchTracks(String normalizedQuery) {
        String requestedToken = tokenManager.getToken();
        try {
            return requestSearch(normalizedQuery, requestedToken);
        } catch (HttpClientErrorException.Unauthorized exception) {
            // 중요: 만료된 공급자 토큰만 폐기하고 사용자 인증 상태에는 영향을 주지 않는다.
            tokenManager.invalidate(requestedToken);
            try {
                return requestSearch(normalizedQuery, tokenManager.getToken());
            } catch (RestClientException retryFailure) {
                throw mapFailure(retryFailure);
            }
        } catch (RestClientException exception) {
            throw mapFailure(exception);
        }
    }

    // 고정된 한국 트랙 검색 조건으로 Spotify를 호출하고 내부 결과로 변환한다.
    private List<MusicSearchResult> requestSearch(String query, String token) {
        // 중요: 최초 요청과 401 재시도 모두 실제 외부 호출 직전에 permit을 소비한다.
        rateLimiter.acquire();
        SpotifySearchResponse response = restClient.get()
                .uri(properties.apiBaseUrl() + "/v1/search?q={q}&type=track&market=KR&limit=10",
                        query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(SpotifySearchResponse.class);
        if (response == null || response.tracks() == null
                || response.tracks().items() == null) {
            throw SpotifyApiException.unavailable(
                    new IllegalStateException("Invalid Spotify search response"));
        }
        return response.tracks().items().stream()
                .filter(Objects::nonNull)
                .filter(SpotifyTrack::hasRequiredFields)
                .map(SpotifyTrack::toResult)
                .toList();
    }

    // Spotify HTTP 오류를 rate limit 또는 공급자 장애로 분류한다.
    private SpotifyApiException mapFailure(RestClientException exception) {
        if (exception instanceof HttpClientErrorException.TooManyRequests rateLimit) {
            if (isQuotaExceeded(rateLimit)) {
                return SpotifyApiException.unavailable(
                        new IllegalStateException("Spotify quota exceeded"));
            }
            Duration retryAfter = retryAfter(rateLimit);
            rateLimiter.block(retryAfter);
            return SpotifyApiException.rateLimited(retryAfter);
        }
        return SpotifyApiException.unavailable(exception);
    }

    // Spotify 429 응답의 구조화된 reason으로 장기 quota 초과를 구분한다.
    private boolean isQuotaExceeded(RestClientResponseException exception) {
        try {
            JsonNode root = objectMapper.readTree(exception.getResponseBodyAsString());
            return root != null && "QUOTA_EXCEEDED".equals(
                    root.path("error").path("reason").asString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    // Retry-After 숫자 초를 파싱하며 잘못된 값은 1초로 안전하게 대체한다.
    private Duration retryAfter(RestClientResponseException exception) {
        String value = exception.getResponseHeaders() == null ? null
                : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        try {
            long seconds = Long.parseLong(value);
            return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_RETRY_AFTER;
        } catch (NumberFormatException ignored) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    // 운영 RestClient에 연결·읽기 timeout을 적용한다.
    private static RestClient.Builder configuredBuilder(RestClient.Builder builder,
            SpotifyProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return builder.requestFactory(factory);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifySearchResponse(SpotifyTracks tracks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifyTracks(List<SpotifyTrack> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifyTrack(
            String id,
            String name,
            List<SpotifyArtist> artists,
            SpotifyAlbum album,
            @JsonProperty("external_urls") SpotifyExternalUrls externalUrls
    ) {
        // 검색 결과에 필요한 출처 표시 필드가 모두 있는지 확인한다.
        private boolean hasRequiredFields() {
            return hasText(id) && hasText(name) && artists != null && !artists.isEmpty()
                    && artists.getFirst() != null && hasText(artists.getFirst().name())
                    && album != null
                    && album.images() != null && !album.images().isEmpty()
                    && album.images().getFirst() != null
                    && hasText(album.images().getFirst().url()) && externalUrls != null
                    && hasText(externalUrls.spotify());
        }

        // Spotify 전용 DTO를 공급자 독립 검색 결과로 변환한다.
        private MusicSearchResult toResult() {
            return new MusicSearchResult(MusicProvider.SPOTIFY, id, name,
                    artists.getFirst().name(), album.images().getFirst().url(),
                    externalUrls.spotify());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifyArtist(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifyAlbum(List<SpotifyImage> images) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifyImage(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpotifyExternalUrls(String spotify) {
    }

    // null 또는 공백 문자열을 필수 필드에서 제외한다.
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
