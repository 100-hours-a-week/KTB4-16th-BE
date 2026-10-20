package com.ktb4.team16.mulo.recommendation.client;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ai", name = "mock-enabled", havingValue = "true")
public class MockRecommendationAiClient implements RecommendationAiClient {
    // 개발 환경에서도 누적 제외 목록을 반영해 최대 세 번의 서로 다른 5곡을 반환한다.
    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        var initialTracks = List.of(
                new RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "Mock Track 1", "MULO",
                        "https://images.example.com/mock-1.jpg", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"),
                new RecommendedTrack("0VjIjW4GlUZAMYd2vXMi3b", "Mock Track 2", "MULO",
                        "https://images.example.com/mock-2.jpg", "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"),
                new RecommendedTrack("7qiZfU4dY1lWllzX7mPBI3", "Mock Track 3", "MULO",
                        "https://images.example.com/mock-3.jpg", "https://open.spotify.com/track/7qiZfU4dY1lWllzX7mPBI3"),
                new RecommendedTrack("1BxfuPKGuaTgP7aM0Bbdwr", "Mock Track 4", "MULO",
                        "https://images.example.com/mock-4.jpg", "https://open.spotify.com/track/1BxfuPKGuaTgP7aM0Bbdwr"),
                new RecommendedTrack("3n3Ppam7vgaVa1iaRUc9Lp", "Mock Track 5", "MULO",
                        "https://images.example.com/mock-5.jpg", "https://open.spotify.com/track/3n3Ppam7vgaVa1iaRUc9Lp"));
        var extraTracks = java.util.stream.IntStream.rangeClosed(6, 15).mapToObj(index -> {
            String id = "muloMock%014d".formatted(index);
            return new RecommendedTrack(id, "Mock Track " + index, "MULO",
                    "https://images.example.com/mock-" + index + ".jpg", "https://open.spotify.com/track/" + id);
        });
        var tracks = java.util.stream.Stream.concat(initialTracks.stream(), extraTracks)
                .filter(track -> !context.excludeTrackIds().contains(track.externalTrackId()))
                .limit(5).toList();
        return new RecommendationResult(tracks, false);
    }
}
