package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.recommendation.repository.RecommendationPlaylistRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RecommendationPlaylistWriterMysqlIntegrationTest {
    @Autowired private UserRepository userRepository;
    @Autowired private RecommendationPlaylistRepository playlistRepository;
    @Autowired private AiRecommendationResultRepository resultRepository;
    @Autowired private RecommendationPlaylistWriter writer;
    @MockitoBean private AiRecommendationResultTrackRepository resultTrackRepository;
    private Long testUserId;

    @AfterEach
    void deleteTestUser() {
        if (testUserId != null) {
            userRepository.deleteById(testUserId);
        }
    }

    @Test
    void rollsBackPlaylistAndHistoryWhenRecommendationTracksCannotBeSaved() {
        User user = userRepository.saveAndFlush(User.signup(
                "recommendation-history-" + UUID.randomUUID() + "@example.com", "password-hash", "qa-user"));
        testUserId = user.getUserId();
        doThrow(new IllegalStateException()).when(resultTrackRepository).saveAll(anyIterable());

        assertThatThrownBy(() -> writer.replace(testUserId, (byte) 1, tracks()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(playlistRepository.findByUserUserId(testUserId)).isEmpty();
        assertThat(resultRepository.countByUserUserId(testUserId)).isZero();
    }

    private List<RecommendedTrack> tracks() {
        return java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> {
                    String trackId = String.format("%022d", index + 1);
                    return new RecommendedTrack(trackId, "Track " + index, "Artist",
                            "https://image.example/album.jpg", "https://open.spotify.com/track/" + trackId);
                })
                .toList();
    }
}
