package com.ktb4.team16.mulo.recommendation.client;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockRecommendationAiClient implements RecommendationAiClient {
    // 실제 AI 연동 전 저장·응답 흐름 검증을 위한 고정 3곡을 반환한다.
    @Override
    public List<RecommendedTrack> recommend(RecommendationContext context) {
        return List.of(
                new RecommendedTrack("4uLU6hMCjMI75M1A2tKUQC", "Mock Track 1", "MULO",
                        "https://images.example.com/mock-1.jpg", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"),
                new RecommendedTrack("0VjIjW4GlUZAMYd2vXMi3b", "Mock Track 2", "MULO",
                        "https://images.example.com/mock-2.jpg", "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b"),
                new RecommendedTrack("7qiZfU4dY1lWllzX7mPBI3", "Mock Track 3", "MULO",
                        "https://images.example.com/mock-3.jpg", "https://open.spotify.com/track/7qiZfU4dY1lWllzX7mPBI3"));
    }
}
