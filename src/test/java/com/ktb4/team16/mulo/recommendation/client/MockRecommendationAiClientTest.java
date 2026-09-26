package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.weather.domain.WeatherCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MockRecommendationAiClientTest {
    @Test
    void returnsExactlyThreeTracks() {
        MockRecommendationAiClient client = new MockRecommendationAiClient();

        var result = client.recommend(new RecommendationAiClient.RecommendationContext(
                WeatherCondition.CLEAR, BigDecimal.valueOf(20), OffsetDateTime.now()));

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(track -> assertThat(track.externalTrackId()).hasSize(22));
    }
}
