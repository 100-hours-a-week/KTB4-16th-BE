package com.ktb4.team16.mulo.vote.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class VoteDomainExceptionTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMissingQuestionToNotFound() {
        var response = handler.handleVoteDomain(new VoteDomainException(ErrorCode.TODAY_VOTE_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.TODAY_VOTE_NOT_FOUND);
    }

    @Test
    void mapsSnapshotNotReadyToServiceUnavailable() {
        var response = handler.handleVoteDomain(new VoteDomainException(ErrorCode.VOTE_SNAPSHOT_NOT_READY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
    }
}
