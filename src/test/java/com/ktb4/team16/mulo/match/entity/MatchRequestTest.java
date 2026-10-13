package com.ktb4.team16.mulo.match.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchRequestTest {

    @Test
    void expiresWaitingRequest() {
        MatchRequest request = waitingRequest();

        request.expire();

        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.EXPIRED);
        assertThat(request.getMatch()).isNull();
    }

    @Test
    void rejectsExpirationForMatchingRequest() {
        MatchRequest request = waitingRequest();
        request.startMatching(Match.create(org.mockito.Mockito.mock(VoteQuestion.class)));

        assertThatThrownBy(request::expire).isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.MATCHING);
    }

    @Test
    void rejectsExpirationForMatchedRequest() {
        MatchRequest request = waitingRequest();
        ReflectionTestUtils.setField(request, "status", MatchRequestStatus.MATCHED);
        ReflectionTestUtils.setField(request, "match", Match.create(org.mockito.Mockito.mock(VoteQuestion.class)));

        assertThatThrownBy(request::expire).isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
    }

    @Test
    void rejectsRepeatedExpiration() {
        MatchRequest request = waitingRequest();
        request.expire();

        assertThatThrownBy(request::expire).isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(MatchRequestStatus.EXPIRED);
    }

    private MatchRequest waitingRequest() {
        return MatchRequest.waiting(
                User.signup("user@example.test", "password-hash", "user"),
                org.mockito.Mockito.mock(VoteAnswer.class));
    }
}
