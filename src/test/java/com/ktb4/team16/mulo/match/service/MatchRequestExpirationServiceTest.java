package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchRequestExpirationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Mock
    private VoteQuestionRepository voteQuestionRepository;
    @Mock
    private MatchRequestRepository matchRequestRepository;

    private MatchRequestExpirationService expirationService;
    private VoteQuestion question;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T14:00:00Z"), KST);
        expirationService = new MatchRequestExpirationService(
                voteQuestionRepository, matchRequestRepository, clock);
        question = org.mockito.Mockito.mock(VoteQuestion.class);
    }

    @Test
    void expiresOnlyWaitingRequestsForQuestionsDatedToday() {
        MatchRequest waiting = MatchRequest.waiting(
                User.signup("waiting@example.test", "password-hash", "waiting"),
                org.mockito.Mockito.mock(VoteAnswer.class));
        when(voteQuestionRepository.findVoteQuestionIdsByVoteDate(TODAY)).thenReturn(List.of(201L));
        when(voteQuestionRepository.findByVoteQuestionIdForUpdate(201L)).thenReturn(Optional.of(question));
        when(matchRequestRepository.findByVoteQuestionIdAndStatusForUpdate(
                201L, MatchRequestStatus.WAITING)).thenReturn(List.of(waiting));

        int expiredCount = expirationService.expireTodayWaitingRequests();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(waiting.getStatus()).isEqualTo(MatchRequestStatus.EXPIRED);
        assertThat(waiting.getMatch()).isNull();
        verify(voteQuestionRepository).findVoteQuestionIdsByVoteDate(TODAY);
        verify(voteQuestionRepository).findByVoteQuestionIdForUpdate(201L);
        verify(matchRequestRepository).findByVoteQuestionIdAndStatusForUpdate(
                201L, MatchRequestStatus.WAITING);
    }
}
