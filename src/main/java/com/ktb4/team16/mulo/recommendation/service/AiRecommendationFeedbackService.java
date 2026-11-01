package com.ktb4.team16.mulo.recommendation.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.recommendation.dto.request.AiRecommendationFeedbackRequest;
import com.ktb4.team16.mulo.recommendation.dto.response.AiRecommendationFeedbackResponse;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedback;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationFeedbackType;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationResult;
import com.ktb4.team16.mulo.recommendation.entity.AiRecommendationType;
import com.ktb4.team16.mulo.recommendation.exception.AiRecommendationFeedbackConstraintViolationTranslator;
import com.ktb4.team16.mulo.recommendation.exception.AiRecommendationFeedbackException;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationFeedbackRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultRepository;
import com.ktb4.team16.mulo.recommendation.repository.AiRecommendationResultTrackRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiRecommendationFeedbackService {

    private final AiRecommendationResultRepository resultRepository;
    private final AiRecommendationResultTrackRepository resultTrackRepository;
    private final AiRecommendationFeedbackRepository feedbackRepository;
    private final Clock clock;

    @Transactional
    public AiRecommendationFeedbackResponse submit(
            Long userId, Long recommendationId, AiRecommendationFeedbackRequest request) {
        if (userId == null) {
            throw new UnauthenticatedUserException();
        }

        AiRecommendationResult recommendation = resultRepository.findById(recommendationId)
                .orElseThrow(() -> new AiRecommendationFeedbackException(ErrorCode.AI_RECOMMENDATION_NOT_FOUND));
        if (!userId.equals(recommendation.getUser().getUserId())) {
            throw new AiRecommendationFeedbackException(ErrorCode.AI_RECOMMENDATION_NOT_OWNED);
        }
        validateSelectedTrack(recommendation, request);
        if (feedbackRepository.existsByRecommendationId(recommendationId)) {
            throw new AiRecommendationFeedbackException(
                    ErrorCode.AI_RECOMMENDATION_FEEDBACK_ALREADY_SUBMITTED);
        }

        AiRecommendationFeedback feedback = AiRecommendationFeedback.create(
                recommendation, recommendation.getUser(), request.feedback(), request.selectedTrackId(),
                LocalDateTime.now(clock));
        try {
            AiRecommendationFeedback saved = feedbackRepository.saveAndFlush(feedback);
            return new AiRecommendationFeedbackResponse("추천 피드백을 제출했습니다.",
                    new AiRecommendationFeedbackResponse.Data(saved.getId(), recommendationId,
                            saved.getFeedback(), saved.getSelectedTrackId()));
        } catch (DataIntegrityViolationException exception) {
            throw AiRecommendationFeedbackConstraintViolationTranslator.translate(exception);
        }
    }

    private void validateSelectedTrack(
            AiRecommendationResult recommendation, AiRecommendationFeedbackRequest request) {
        if (request.feedback() == null) {
            throw invalidInput();
        }
        String selectedTrackId = request.selectedTrackId();
        if (recommendation.getRecommendationType() == AiRecommendationType.PHOTO) {
            if (selectedTrackId != null) {
                throw invalidInput();
            }
            return;
        }
        if (request.feedback() == AiRecommendationFeedbackType.DISLIKE) {
            if (selectedTrackId != null) {
                throw invalidInput();
            }
            return;
        }
        if (selectedTrackId == null
                || !resultTrackRepository.existsByRecommendationIdAndExternalTrackId(
                        recommendation.getId(), selectedTrackId)) {
            throw invalidInput();
        }
    }

    private AiRecommendationFeedbackException invalidInput() {
        return new AiRecommendationFeedbackException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
