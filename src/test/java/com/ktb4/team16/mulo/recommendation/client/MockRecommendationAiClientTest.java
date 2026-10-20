package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockRecommendationAiClientTest {
    @Test
    void returnsExactlyFiveTracks() {
        MockRecommendationAiClient client = new MockRecommendationAiClient();

        var result = client.recommend(new RecommendationAiClient.RecommendationContext(
                7L, WeatherCondition.CLEAR, BigDecimal.valueOf(20), OffsetDateTime.now(), List.of(), null, List.of()));

        assertThat(result.degraded()).isFalse();
        assertThat(result.tracks()).hasSize(5);
        assertThat(result.tracks()).allSatisfy(track -> assertThat(track.externalTrackId()).hasSize(22));
    }
    // 개발용 AI에서도 세 번의 추천이 서로 겹치지 않게 제외 목록을 반영한다.
    @Test
    void excludesTracksAcrossThreeRecommendations() {
        MockRecommendationAiClient client = new MockRecommendationAiClient();
        var seen = new java.util.ArrayList<String>();
        for (int round = 0; round < 3; round++) {
            var result = client.recommend(new RecommendationAiClient.RecommendationContext(
                    7L, WeatherCondition.CLEAR, BigDecimal.valueOf(20), OffsetDateTime.now(), List.of(), null,
                    List.copyOf(seen)));
            assertThat(result.tracks()).hasSize(5);
            assertThat(result.tracks()).allSatisfy(track -> assertThat(seen).doesNotContain(track.externalTrackId()));
            result.tracks().forEach(track -> seen.add(track.externalTrackId()));
        }
        assertThat(seen).doesNotHaveDuplicates().hasSize(15);
    }

}
