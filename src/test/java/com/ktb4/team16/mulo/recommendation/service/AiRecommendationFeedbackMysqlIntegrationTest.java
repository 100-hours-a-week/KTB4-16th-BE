package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.recommendation.dto.request.AiRecommendationFeedbackRequest;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedbackType;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResultTrack;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationType;
import com.ktb4.team16.mulo.recommendation.exception.AiRecommendationFeedbackException;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationFeedbackRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiRecommendationFeedbackMysqlIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AiRecommendationResultRepository resultRepository;
    @Autowired private AiRecommendationResultTrackRepository resultTrackRepository;
    @Autowired private AiRecommendationFeedbackRepository feedbackRepository;
    @Autowired private AiRecommendationFeedbackService feedbackService;

    private final List<Long> testUserIds = new ArrayList<>();
    private final List<Long> recommendationIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long recommendationId : recommendationIds) {
            jdbcTemplate.update("DELETE FROM ai_recommendation_feedbacks WHERE recommendation_id = ?",
                    recommendationId);
            jdbcTemplate.update("DELETE FROM ai_recommendation_result_tracks WHERE recommendation_id = ?",
                    recommendationId);
            jdbcTemplate.update("DELETE FROM ai_recommendation_results WHERE id = ?", recommendationId);
        }
        for (Long userId : testUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
    }

    @Test
    void savesPhotoLikeAndDislike() {
        User user = createUser("photo");
        AiRecommendationResult likeResult = createResult(user, AiRecommendationType.PHOTO, null, List.of());
        AiRecommendationResult dislikeResult = createResult(user, AiRecommendationType.PHOTO, null, List.of());

        var like = feedbackService.submit(user.getUserId(), likeResult.getId(),
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null));
        var dislike = feedbackService.submit(user.getUserId(), dislikeResult.getId(),
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null));

        assertThat(like.data().recommendationId()).isEqualTo(likeResult.getId());
        assertThat(dislike.data().recommendationId()).isEqualTo(dislikeResult.getId());
        assertStored(likeResult.getId(), user.getUserId(), "LIKE", null);
        assertStored(dislikeResult.getId(), user.getUserId(), "DISLIKE", null);
    }

    @Test
    void savesPlaylistLikeWithTrackFromThatRecommendation() {
        User user = createUser("playlistLike");
        String selectedTrackId = spotifyId(1);
        AiRecommendationResult recommendation = createResult(user, AiRecommendationType.PLAYLIST,
                (byte) 1, List.of(selectedTrackId, spotifyId(2)));

        var response = feedbackService.submit(user.getUserId(), recommendation.getId(),
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, selectedTrackId));

        assertThat(response.data().feedbackId()).isNotNull();
        assertThat(response.data().selectedTrackId()).isEqualTo(selectedTrackId);
        assertStored(recommendation.getId(), user.getUserId(), "LIKE", selectedTrackId);
    }

    @Test
    void savesPlaylistDislikeWithoutTrack() {
        User user = createUser("playlistDislike");
        AiRecommendationResult recommendation = createResult(user, AiRecommendationType.PLAYLIST,
                (byte) 2, List.of(spotifyId(3)));

        feedbackService.submit(user.getUserId(), recommendation.getId(),
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null));

        assertStored(recommendation.getId(), user.getUserId(), "DISLIKE", null);
    }

    @Test
    void rejectsMissingAndForeignRecommendationResults() {
        User owner = createUser("owner");
        User other = createUser("other");
        AiRecommendationResult recommendation = createResult(owner, AiRecommendationType.PHOTO, null, List.of());

        assertThatThrownBy(() -> feedbackService.submit(owner.getUserId(), recommendation.getId() + 100000L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_NOT_FOUND);
        assertThatThrownBy(() -> feedbackService.submit(other.getUserId(), recommendation.getId(),
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_NOT_OWNED);
        assertThat(feedbackRepository.existsByRecommendationId(recommendation.getId())).isFalse();
    }

    @Test
    void rejectsDuplicateAndLeavesOneDatabaseRow() {
        User user = createUser("duplicate");
        AiRecommendationResult recommendation = createResult(user, AiRecommendationType.PHOTO, null, List.of());
        AiRecommendationFeedbackRequest request =
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null);

        feedbackService.submit(user.getUserId(), recommendation.getId(), request);

        assertThatThrownBy(() -> feedbackService.submit(user.getUserId(), recommendation.getId(), request))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_FEEDBACK_ALREADY_SUBMITTED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_recommendation_feedbacks "
                + "WHERE recommendation_id = ?", Integer.class, recommendation.getId())).isEqualTo(1);
    }

    @Test
    void concurrentSubmissionsAreSerializedByUniqueConstraint() throws Exception {
        User user = createUser("race");
        AiRecommendationResult recommendation = createResult(user, AiRecommendationType.PHOTO, null, List.of());
        AiRecommendationFeedbackRequest request =
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(submitAfterBarrier(
                    user.getUserId(), recommendation.getId(), request, ready, start));
            Future<Boolean> second = executor.submit(submitAfterBarrier(
                    user.getUserId(), recommendation.getId(), request, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_recommendation_feedbacks "
                    + "WHERE recommendation_id = ?", Integer.class, recommendation.getId())).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Callable<Boolean> submitAfterBarrier(Long userId, Long recommendationId,
            AiRecommendationFeedbackRequest request, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 피드백 테스트를 시작하지 못했습니다.");
            }
            try {
                feedbackService.submit(userId, recommendationId, request);
                return true;
            } catch (AiRecommendationFeedbackException exception) {
                if (exception.errorCode() == ErrorCode.AI_RECOMMENDATION_FEEDBACK_ALREADY_SUBMITTED) {
                    return false;
                }
                throw exception;
            }
        };
    }

    private User createUser(String prefix) {
        User user = userRepository.saveAndFlush(User.signup(
                "feedback-" + prefix + "-" + UUID.randomUUID() + "@example.com", "password-hash",
                "fb" + UUID.randomUUID().toString().substring(0, 8)));
        testUserIds.add(user.getUserId());
        return user;
    }

    private AiRecommendationResult createResult(
            User user, AiRecommendationType type, Byte attempt, List<String> trackIds) {
        AiRecommendationResult recommendation = resultRepository.saveAndFlush(
                AiRecommendationResult.create(user, type, attempt, LocalDateTime.now()));
        recommendationIds.add(recommendation.getId());
        List<AiRecommendationResultTrack> tracks = java.util.stream.IntStream.range(0, trackIds.size())
                .mapToObj(index -> AiRecommendationResultTrack.create(recommendation, trackIds.get(index), index + 1))
                .toList();
        if (!tracks.isEmpty()) {
            resultTrackRepository.saveAllAndFlush(tracks);
        }
        return recommendation;
    }

    private void assertStored(Long recommendationId, Long userId, String feedback, String selectedTrackId) {
        var row = jdbcTemplate.queryForMap("SELECT user_id, feedback, selected_track_id "
                + "FROM ai_recommendation_feedbacks WHERE recommendation_id = ?", recommendationId);
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId);
        assertThat(row.get("feedback")).isEqualTo(feedback);
        assertThat(row.get("selected_track_id")).isEqualTo(selectedTrackId);
    }

    private String spotifyId(int suffix) {
        return String.format("%022d", suffix);
    }
}
