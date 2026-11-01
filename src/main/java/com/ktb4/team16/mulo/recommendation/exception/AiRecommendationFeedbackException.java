package com.ktb4.team16.mulo.recommendation.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

public class AiRecommendationFeedbackException extends RuntimeException {

    private final ErrorCode errorCode;

    public AiRecommendationFeedbackException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public AiRecommendationFeedbackException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
