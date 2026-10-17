package com.ktb4.team16.mulo.vote.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

public final class VoteAnswerConstraintViolationTranslator {

    private static final String UNIQUE_CONSTRAINT = "uk_vote_answers_user_question";

    private VoteAnswerConstraintViolationTranslator() { }

    public static RuntimeException translate(DataIntegrityViolationException exception) {
        if (hasVoteAnswerUniqueConstraint(exception)) {
            return new VoteDomainException(ErrorCode.VOTE_ALREADY_ANSWERED, exception);
        }
        return exception;
    }

    private static boolean hasVoteAnswerUniqueConstraint(Throwable throwable) {
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
