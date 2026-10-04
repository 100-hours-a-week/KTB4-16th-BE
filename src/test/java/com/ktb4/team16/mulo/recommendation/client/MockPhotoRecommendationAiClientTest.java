package com.ktb4.team16.mulo.recommendation.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockPhotoRecommendationAiClientTest {
    @Test
    void returnsThreeTracksWithAllRequiredFields() {
        var response = new MockPhotoRecommendationAiClient().recommend("https://signed.example/photo");

        assertThat(response.degraded()).isFalse();
        assertThat(response.tracks()).hasSize(3);
        assertThat(response.tracks()).allSatisfy(track -> {
            assertThat(track.externalTrackId()).isNotBlank();
            assertThat(track.title()).isNotBlank();
            assertThat(track.artistName()).isNotBlank();
            assertThat(track.albumImageUrl()).isNotBlank();
            assertThat(track.externalUrl()).isNotBlank();
        });
    }
}
