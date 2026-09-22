package com.ktb4.team16.mulo.music.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb4.team16.mulo.music.config.SpotifyProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SpotifyTokenManager {
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final SpotifyProperties properties;
    private final Clock clock;
    private final Object refreshLock = new Object();
    private volatile CachedToken cachedToken;

    // 운영용 HTTP timeout을 적용한 토큰 관리자를 생성한다.
    @Autowired
    public SpotifyTokenManager(SpotifyProperties properties, Clock clock) {
        this(configuredBuilder(RestClient.builder(), properties), properties, clock);
    }

    // 테스트가 제어하는 RestClient와 Clock으로 토큰 관리자를 생성한다.
    SpotifyTokenManager(RestClient.Builder builder, SpotifyProperties properties, Clock clock) {
        this.restClient = builder.build();
        this.properties = properties;
        this.clock = clock;
    }

    // 유효한 토큰은 재사용하고 만료가 가까우면 한 번만 갱신한다.
    public String getToken() {
        CachedToken current = cachedToken;
        if (isUsable(current)) {
            return current.value();
        }
        synchronized (refreshLock) {
            current = cachedToken;
            if (isUsable(current)) {
                return current.value();
            }
            cachedToken = requestToken();
            return cachedToken.value();
        }
    }

    // 401을 받은 토큰이 현재 캐시와 같을 때만 폐기해 새 토큰을 보호한다.
    public void invalidate(String rejectedToken) {
        synchronized (refreshLock) {
            if (cachedToken != null && cachedToken.value().equals(rejectedToken)) {
                cachedToken = null;
            }
        }
    }

    // 캐시 토큰이 갱신 기준 시각 전인지 확인한다.
    private boolean isUsable(CachedToken token) {
        return token != null && clock.instant().isBefore(token.refreshAt());
    }

    // Client Credentials 요청을 보내 새 Access Token과 갱신 시각을 만든다.
    private CachedToken requestToken() {
        try {
            TokenResponse response = restClient.post()
                    .uri(properties.accountsBaseUrl().resolve("/api/token"))
                    .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null
                    || response.accessToken().isBlank() || response.expiresIn() <= 60) {
                throw new IllegalStateException("Invalid Spotify token response");
            }
            Instant refreshAt = clock.instant()
                    .plusSeconds(response.expiresIn())
                    .minus(REFRESH_MARGIN);
            return new CachedToken(response.accessToken(), refreshAt);
        } catch (RestClientException | IllegalStateException exception) {
            throw SpotifyApiException.unavailable(exception);
        }
    }

    // Client ID와 Secret을 Spotify Basic 인증 헤더 형식으로 인코딩한다.
    private String basicAuthorization() {
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
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

    private record CachedToken(String value, Instant refreshAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }
}
