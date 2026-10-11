package com.ktb4.team16.mulo.match.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

public final class MatchRequestConstraintViolationTranslator {

    private static final String VOTE_ANSWER_UNIQUE_CONSTRAINT = "uk_match_requests_vote_answer";

    private MatchRequestConstraintViolationTranslator() {
    }

    public static RuntimeException translate(DataIntegrityViolationException exception) {
        if (hasVoteAnswerUniqueConstraint(exception)) {
            return new MatchDomainException(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS, exception);
        }
        return exception;
    }

    private static boolean hasVoteAnswerUniqueConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().contains(VOTE_ANSWER_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
