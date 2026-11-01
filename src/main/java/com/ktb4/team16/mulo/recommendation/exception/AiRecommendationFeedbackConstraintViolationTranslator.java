package com.ktb4.team16.mulo.recommendation.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

public final class AiRecommendationFeedbackConstraintViolationTranslator {

    private static final String UNIQUE_CONSTRAINT = "uk_ai_recommendation_feedbacks_result";

    private AiRecommendationFeedbackConstraintViolationTranslator() { }

    public static RuntimeException translate(DataIntegrityViolationException exception) {
        if (hasFeedbackUniqueConstraint(exception)) {
            return new AiRecommendationFeedbackException(
                    ErrorCode.AI_RECOMMENDATION_FEEDBACK_ALREADY_SUBMITTED, exception);
        }
        return exception;
    }

    private static boolean hasFeedbackUniqueConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
