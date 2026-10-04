package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb4.team16.mulo.recommendation.config.AiProperties;
import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ContextRecommendationAiClientTest {
    // AI 명세의 필수값과 선택 문맥(주변 곡·법정동 이름)을 함께 전송한다.
    @Test
    void sendsRequiredContextAndMapsValidRecommendation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(content().json("""
                        {"userId":7,"weather":{"condition":"RAIN","temperature":16.0},
                        "localTime":"2026-09-27T19:30:00+09:00","nearbyTracks":[
                        {"title":"비도 오고 그래서","artistName":"헤이즈","count":9}],
                        "limit":5,"place":{"name":"태평로1가"}}
                        """))
                .andRespond(withSuccess(responseWithTracks(5, false), MediaType.APPLICATION_JSON));

        var result = client.recommend(context());

        assertThat(result.degraded()).isFalse();
        assertThat(result.tracks()).hasSize(5);
        assertThat(result.tracks().getFirst()).satisfies(track -> {
            assertThat(track.title()).isEqualTo("Track 1");
            assertThat(track.externalTrackId()).isEqualTo("track-1");
        });
        server.verify();
    }

    // 선택 문맥이 비어 있으면 빈 배열·빈 객체 대신 JSON 필드 자체를 생략한다.
    @Test
    void omitsOptionalContextWhenNearbyTracksAndPlaceAreUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andExpect(content().json("""
                        {"userId":7,"weather":{"condition":"RAIN","temperature":16.0},
                        "localTime":"2026-09-27T19:30:00+09:00","limit":5}
                        """))
                .andExpect(jsonPath("$.nearbyTracks").doesNotExist())
                .andExpect(jsonPath("$.place").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andRespond(withSuccess(responseWithTracks(5, false), MediaType.APPLICATION_JSON));

        var result = client.recommend(new RecommendationAiClient.RecommendationContext(7L, WeatherCondition.RAIN,
                BigDecimal.valueOf(16.0), OffsetDateTime.parse("2026-09-27T19:30:00+09:00"), List.of(), null));

        assertThat(result.tracks()).hasSize(5);
        server.verify();
    }

    // 정확히 5곡을 포함한 저하 응답은 기존 degraded 상태를 보존한다.
    @Test
    void mapsDegradedRecommendationWithExactlyFiveTracks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andRespond(withSuccess(responseWithTracks(5, true), MediaType.APPLICATION_JSON));

        var result = client.recommend(context());

        assertThat(result.degraded()).isTrue();
        assertThat(result.tracks()).hasSize(5);
        server.verify();
    }

    @Test
    void rejectsFourTrackResponse() {
        assertInvalidTrackCountResponse(4);
    }

    @Test
    void rejectsSixTrackResponse() {
        assertInvalidTrackCountResponse(6);
    }

    // DB 필수값인 앨범 이미지가 없으면 저장 전에 AI 연동 오류로 차단한다.
    @Test
    void rejectsTrackWithoutAlbumImageUrl() {
        assertInvalidTrackResponse("""
                {"title":"Beautiful","artistName":"Crush",
                "externalTrackId":"6mzF8HvHdVrzJNd8M1uFCS","albumImageUrl":null,
                "externalUrl":"https://open.spotify.com/track/6mzF8HvHdVrzJNd8M1uFCS"}
                """);
    }

    // AI의 곡 식별자·제목·아티스트·외부 URL 누락은 신뢰할 수 없는 응답으로 처리한다.
    @Test
    void rejectsTrackWithMissingRequiredField() {
        assertInvalidTrackResponse("""
                {"title":"Beautiful","artistName":"Crush",
                "albumImageUrl":"https://image.example/album.jpg",
                "externalUrl":"https://open.spotify.com/track/6mzF8HvHdVrzJNd8M1uFCS"}
                """);
    }

    // AI HTTP 오류는 내부 상세를 노출하지 않는 도메인 예외로 변환한다.
    @Test
    void convertsNonSuccessResponseToRecommendationAiException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"UPSTREAM_UNAVAILABLE\"}"));

        assertThatThrownBy(() -> client.recommend(context()))
                .isInstanceOf(RecommendationAiException.class);
        server.verify();
    }

    // JSON 역직렬화 실패도 같은 안전한 AI 연동 예외로 변환한다.
    @Test
    void convertsMalformedJsonToRecommendationAiException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andRespond(withSuccess("{not-json}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.recommend(context()))
                .isInstanceOf(RecommendationAiException.class);
        server.verify();
    }

    // AI 응답의 곡 개수가 정확히 5개가 아니면 추천 예외로 변환한다.
    private void assertInvalidTrackCountResponse(int count) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andRespond(withSuccess(responseWithTracks(count, false), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.recommend(context()))
                .isInstanceOf(RecommendationAiException.class);
        server.verify();
    }

    // 필수 곡 필드가 빠진 5곡 응답의 예외 변환을 검증한다.
    private void assertInvalidTrackResponse(String invalidTrackJson) {
        String responseBody = "{\"tracks\":[" + validTrackJson(1) + "," + validTrackJson(2)
                + "," + validTrackJson(3) + "," + validTrackJson(4) + "," + invalidTrackJson
                + "],\"degraded\":false}";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ContextRecommendationAiClient client = new ContextRecommendationAiClient(builder, properties());
        server.expect(requestTo("https://mulostudio.com/ai/api/context-recommend"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.recommend(context()))
                .isInstanceOf(RecommendationAiException.class);
        server.verify();
    }

    private String responseWithTracks(int count, boolean degraded) {
        String tracks = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(this::validTrackJson)
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"tracks\":[" + tracks + "],\"degraded\":" + degraded + "}";
    }

    private String validTrackJson(int index) {
        return "{\"title\":\"Track " + index + "\",\"artistName\":\"Artist\","
                + "\"externalTrackId\":\"track-" + index + "\","
                + "\"albumImageUrl\":\"https://image.example/album.jpg\","
                + "\"externalUrl\":\"https://open.spotify.com/track/track-" + index + "\"}";
    }

    // 모든 HTTP 경계 테스트가 공유하는 명세 기반 AI 설정을 생성한다.
    private AiProperties properties() {
        return new AiProperties(URI.create("https://mulostudio.com/ai"), "test-token",
                Duration.ofSeconds(1), Duration.ofSeconds(1), false);
    }

    // KST 오프셋을 포함한 AI 추천 요청 문맥을 생성한다.
    private RecommendationAiClient.RecommendationContext context() {
        return new RecommendationAiClient.RecommendationContext(7L, WeatherCondition.RAIN,
                BigDecimal.valueOf(16.0), OffsetDateTime.parse("2026-09-27T19:30:00+09:00"),
                List.of(new RecommendationAiClient.NearbyTrack("비도 오고 그래서", "헤이즈", 9)), "태평로1가");
    }
}
