package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockRecommendationAiClientTest {
    @Test
    void returnsExactlyThreeTracks() {
        MockRecommendationAiClient client = new MockRecommendationAiClient();

        var result = client.recommend(new RecommendationAiClient.RecommendationContext(
                7L, WeatherCondition.CLEAR, BigDecimal.valueOf(20), OffsetDateTime.now(), List.of(), null));

        assertThat(result.degraded()).isFalse();
        assertThat(result.tracks()).hasSize(3);
        assertThat(result.tracks()).allSatisfy(track -> assertThat(track.externalTrackId()).hasSize(22));
    }
}
