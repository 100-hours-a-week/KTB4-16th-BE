package com.ktb4.team16.mulo.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.recommendation.dto.request.AiRecommendationFeedbackRequest;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedback;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedbackType;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationType;
import com.ktb4.team16.mulo.recommendation.exception.AiRecommendationFeedbackException;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationFeedbackRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import com.ktb4.team16.mulo.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AiRecommendationFeedbackServiceTest {

    @Mock private AiRecommendationResultRepository resultRepository;
    @Mock private AiRecommendationResultTrackRepository resultTrackRepository;
    @Mock private AiRecommendationFeedbackRepository feedbackRepository;
    @Mock private AiRecommendationResult recommendation;
    @Mock private User owner;
    @Mock private AiRecommendationFeedback savedFeedback;

    private AiRecommendationFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AiRecommendationFeedbackService(resultRepository, resultTrackRepository,
                feedbackRepository, Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void savesPhotoLikeWithoutSelectedTrack() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PHOTO);
        stubSaving(AiRecommendationFeedbackType.LIKE, null);

        var response = service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null));

        assertThat(response.data().feedbackId()).isEqualTo(7L);
        assertThat(response.data().recommendationId()).isEqualTo(42L);
        assertThat(response.data().selectedTrackId()).isNull();
        assertThat(capturedFeedback().getValue().getUser()).isSameAs(owner);
        assertThat(capturedFeedback().getValue().getCreatedAt())
                .isEqualTo(java.time.LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    void savesPhotoDislikeWithoutSelectedTrack() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PHOTO);
        stubSaving(AiRecommendationFeedbackType.DISLIKE, null);

        service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null));

        assertThat(capturedFeedback().getValue().getFeedback()).isEqualTo(AiRecommendationFeedbackType.DISLIKE);
        assertThat(capturedFeedback().getValue().getSelectedTrackId()).isNull();
    }

    @Test
    void savesPlaylistLikeForTrackInRecommendation() {
        stubRecommendation();
        when(recommendation.getId()).thenReturn(42L);
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PLAYLIST);
        stubSaving(AiRecommendationFeedbackType.LIKE, "track-1");
        when(feedbackRepository.existsByRecommendationId(42L)).thenReturn(false);
        when(resultTrackRepository.existsByRecommendationIdAndExternalTrackId(42L, "track-1"))
                .thenReturn(true);

        var response = service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, "track-1"));

        assertThat(response.data().selectedTrackId()).isEqualTo("track-1");
        assertThat(capturedFeedback().getValue().getSelectedTrackId()).isEqualTo("track-1");
    }

    @Test
    void savesPlaylistDislikeWithoutSelectedTrack() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PLAYLIST);
        stubSaving(AiRecommendationFeedbackType.DISLIKE, null);
        when(feedbackRepository.existsByRecommendationId(42L)).thenReturn(false);

        service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null));

        assertThat(capturedFeedback().getValue().getSelectedTrackId()).isNull();
        verify(resultTrackRepository, never()).existsByRecommendationIdAndExternalTrackId(any(), any());
    }

    @Test
    void rejectsMissingRecommendation() {
        when(resultRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(17L, 99L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_NOT_FOUND);
    }

    @Test
    void rejectsRecommendationOwnedByAnotherUser() {
        stubRecommendation();

        assertThatThrownBy(() -> service.submit(18L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.DISLIKE, null)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_NOT_OWNED);
        verify(feedbackRepository, never()).saveAndFlush(any(AiRecommendationFeedback.class));
    }

    @Test
    void rejectsDuplicateBeforeSaving() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PHOTO);
        when(feedbackRepository.existsByRecommendationId(42L)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_FEEDBACK_ALREADY_SUBMITTED);
        verify(feedbackRepository, never()).saveAndFlush(any(AiRecommendationFeedback.class));
    }

    @Test
    void rejectsTrackSelectionForPhoto() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PHOTO);

        assertInvalid(AiRecommendationFeedbackType.LIKE, "track-1");
    }

    @Test
    void requiresTrackForPlaylistLike() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PLAYLIST);

        assertInvalid(AiRecommendationFeedbackType.LIKE, null);
        verify(resultTrackRepository, never()).existsByRecommendationIdAndExternalTrackId(any(), any());
    }

    @Test
    void rejectsTrackNotInCurrentRecommendation() {
        stubRecommendation();
        when(recommendation.getId()).thenReturn(42L);
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PLAYLIST);
        when(resultTrackRepository.existsByRecommendationIdAndExternalTrackId(42L, "other-track"))
                .thenReturn(false);

        assertInvalid(AiRecommendationFeedbackType.LIKE, "other-track");
    }

    @Test
    void rejectsTrackForPlaylistDislike() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PLAYLIST);

        assertInvalid(AiRecommendationFeedbackType.DISLIKE, "track-1");
    }

    @Test
    void translatesConcurrentUniqueConstraintViolation() {
        stubRecommendation();
        when(recommendation.getRecommendationType()).thenReturn(AiRecommendationType.PHOTO);
        when(feedbackRepository.existsByRecommendationId(42L)).thenReturn(false);
        when(feedbackRepository.saveAndFlush(any(AiRecommendationFeedback.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'uk_ai_recommendation_feedbacks_result'"));

        assertThatThrownBy(() -> service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(AiRecommendationFeedbackType.LIKE, null)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_RECOMMENDATION_FEEDBACK_ALREADY_SUBMITTED);
    }

    private void assertInvalid(AiRecommendationFeedbackType feedback, String selectedTrackId) {
        assertThatThrownBy(() -> service.submit(17L, 42L,
                new AiRecommendationFeedbackRequest(feedback, selectedTrackId)))
                .isInstanceOf(AiRecommendationFeedbackException.class)
                .extracting(exception -> ((AiRecommendationFeedbackException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(feedbackRepository, never()).saveAndFlush(any(AiRecommendationFeedback.class));
    }

    private void stubRecommendation() {
        when(resultRepository.findById(42L)).thenReturn(Optional.of(recommendation));
        when(recommendation.getUser()).thenReturn(owner);
        when(owner.getUserId()).thenReturn(17L);
    }

    private void stubSaving(AiRecommendationFeedbackType feedback, String selectedTrackId) {
        when(savedFeedback.getId()).thenReturn(7L);
        when(savedFeedback.getFeedback()).thenReturn(feedback);
        when(savedFeedback.getSelectedTrackId()).thenReturn(selectedTrackId);
        when(feedbackRepository.saveAndFlush(any(AiRecommendationFeedback.class))).thenReturn(savedFeedback);
    }

    private ArgumentCaptor<AiRecommendationFeedback> capturedFeedback() {
        ArgumentCaptor<AiRecommendationFeedback> captor = ArgumentCaptor.forClass(AiRecommendationFeedback.class);
        verify(feedbackRepository).saveAndFlush(captor.capture());
        return captor;
    }
}
