package com.ktb4.team16.mulo.recommendation.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai", name = "mock-enabled", havingValue = "true")
public class MockPhotoRecommendationAiClient implements PhotoRecommendationAiGateway {
    private static final String MOCK_ALBUM_IMAGE_URL =
            "https://placehold.co/600x600/png?text=MULO+Mock+Album";

    @Override
    public PhotoRecommendationAiClient.PhotoRecommendationAiResponse recommend(String imageUrl) {
        return new PhotoRecommendationAiClient.PhotoRecommendationAiResponse(
                java.util.List.of(
                        track("mock-track-1", "MULO Mock Sunset", "MULO Mock Artist 1"),
                        track("mock-track-2", "MULO Mock Ocean", "MULO Mock Artist 2"),
                        track("mock-track-3", "MULO Mock Night", "MULO Mock Artist 3"),
                        track("mock-track-4", "MULO Mock Dawn", "MULO Mock Artist 4"),
                        track("mock-track-5", "MULO Mock Rain", "MULO Mock Artist 5")),
                false);
    }

    private static PhotoRecommendationAiClient.PhotoRecommendationAiResponse.Track track(
            String externalTrackId, String title, String artistName) {
        return new PhotoRecommendationAiClient.PhotoRecommendationAiResponse.Track(
                title, artistName, externalTrackId, "spotify:track:" + externalTrackId,
                MOCK_ALBUM_IMAGE_URL,
                "https://open.spotify.com/track/" + externalTrackId);
    }
}
