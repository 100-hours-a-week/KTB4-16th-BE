package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;

import com.ktb4.team16.mulo.recommendation.client.RecommendationAiClient.RecommendedTrack;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RecommendationPlaylistWriterMysqlIntegrationTest {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AiRecommendationResultRepository resultRepository;
    @Autowired private RecommendationPlaylistWriter writer;
    @MockitoBean private AiRecommendationResultTrackRepository resultTrackRepository;
    private Long testUserId;

    // Flyway 적용 후 화면 복원 전용 테이블이 실제 MySQL 스키마에서 제거됐는지 검증한다.
    @Test
    void removesScreenPlaylistTables() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('recommendation_playlists', 'recommendation_playlist_items')
                """, Integer.class)).isZero();
    }

    @AfterEach
    void deleteTestUser() {
        if (testUserId != null) {
            userRepository.deleteById(testUserId);
        }
    }

    // 평가 곡 기록 실패 시 음악 카탈로그와 결과 이력을 함께 롤백한다.
    @Test
    void rollsBackCatalogueAndHistoryWhenRecommendationTracksCannotBeSaved() {
        User user = userRepository.saveAndFlush(User.signup(
                "recommendation-history-" + UUID.randomUUID() + "@example.com", "password-hash", "qa-user"));
        testUserId = user.getUserId();
        doThrow(new IllegalStateException()).when(resultTrackRepository).saveAll(anyIterable());

        assertThatThrownBy(() -> writer.replace(testUserId, (byte) 1, tracks()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM music_tracks WHERE external_track_id IN (?, ?, ?, ?, ?)",
                Integer.class, "0000000000000000000001", "0000000000000000000002", "0000000000000000000003",
                "0000000000000000000004", "0000000000000000000005")).isZero();
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
