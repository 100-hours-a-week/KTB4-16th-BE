package com.ktb4.team16.mulo.match.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.error.ErrorResponse;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MatchDomainExceptionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMatchDomainErrorsToTheirConfiguredStatuses() {
        assertError(ErrorCode.MATCH_VOTE_ANSWER_NOT_FOUND, HttpStatus.BAD_REQUEST);
        assertError(ErrorCode.MATCH_VOTE_ANSWER_NOT_OWNED, HttpStatus.FORBIDDEN);
        assertError(ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY, HttpStatus.BAD_REQUEST);
        assertError(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS, HttpStatus.CONFLICT);
        assertError(ErrorCode.MATCH_REQUEST_CLOSED, HttpStatus.CONFLICT);
    }

    @Test
    void translatesOnlyVoteAnswerUniqueConstraintViolation() {
        DataIntegrityViolationException duplicate =
                new DataIntegrityViolationException("uk_match_requests_vote_answer");

        RuntimeException translated = MatchRequestConstraintViolationTranslator.translate(duplicate);

        assertThat(translated).isInstanceOf(MatchDomainException.class);
        assertThat(((MatchDomainException) translated).errorCode())
                .isEqualTo(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS);
        assertThat(translated.getCause()).isSameAs(duplicate);
    }

    @Test
    void preservesUnrelatedIntegrityViolation() {
        DataIntegrityViolationException unrelated =
                new DataIntegrityViolationException("another_constraint");

        RuntimeException translated = MatchRequestConstraintViolationTranslator.translate(unrelated);

        assertThat(translated).isSameAs(unrelated);
    }

    private void assertError(ErrorCode errorCode, HttpStatus expectedStatus) {
        ResponseEntity<ErrorResponse> response = handler.handleMatchDomain(
                new MatchDomainException(errorCode));

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody().code()).isEqualTo(errorCode);
        assertThat(response.getBody().message()).isEqualTo(errorCode.message());
    }
}
