package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.entity.Match;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.repository.MatchRepository;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchRequestRepository matchRequestRepository;
    @Mock
    private VoteAnswerRepository voteAnswerRepository;
    @Mock
    private VoteQuestionRepository voteQuestionRepository;

    private MatchService matchService;
    private User requester;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), KST);
        matchService = new MatchService(
                matchRepository, matchRequestRepository, voteAnswerRepository, voteQuestionRepository, clock);
        requester = user(11L);
    }

    @Test
    void rejectsMissingVoteAnswer() {
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_VOTE_ANSWER_NOT_FOUND);

        verify(matchRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsVoteAnswerOwnedByAnotherUser() {
        VoteAnswer answer = mock(VoteAnswer.class);
        when(answer.getUser()).thenReturn(user(12L));
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_VOTE_ANSWER_NOT_OWNED);

        verify(matchRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnswerForAnotherDate() {
        VoteAnswer answer = mock(VoteAnswer.class);
        when(answer.getUser()).thenReturn(requester);
        VoteQuestion oldQuestion = question(TODAY.minusDays(1));
        when(answer.getVoteQuestion()).thenReturn(oldQuestion);
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(answer));
        when(voteQuestionRepository.findByVoteQuestionIdForUpdate(201L)).thenReturn(Optional.of(oldQuestion));

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);

        verify(matchRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExistingMatchRequest() {
        VoteAnswer requesterAnswer = mock(VoteAnswer.class);
        when(requesterAnswer.getVoteAnswerId()).thenReturn(101L);
        when(requesterAnswer.getUser()).thenReturn(requester);
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(requesterAnswer));
        when(matchRequestRepository.existsByVoteAnswer_VoteAnswerId(101L)).thenReturn(true);

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS);

        verify(matchRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsWaitingRequestWhenThereIsNoOppositeCandidate() {
        VoteQuestion question = question(TODAY);
        VoteAnswer requesterAnswer = answer(101L, requester, question);
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(requesterAnswer));
        when(matchRequestRepository.existsByVoteAnswer_VoteAnswerId(101L)).thenReturn(false);
        when(voteQuestionRepository.findByVoteQuestionIdForUpdate(201L)).thenReturn(Optional.of(question));
        when(matchRequestRepository.saveAndFlush(any(MatchRequest.class)))
                .thenAnswer(invocation -> {
                    MatchRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "matchRequestId", 301L);
                    return request;
                });
        when(matchRequestRepository.findOppositeWaitingForUpdate(101L, PageRequest.of(0, 1)))
                .thenReturn(List.of());

        var response = matchService.createMatchRequest(11L, new MatchRequestCreateRequest(101L));

        assertThat(response.data().matchRequestId()).isEqualTo(301L);
        assertThat(response.data().status()).isEqualTo(MatchRequestStatus.WAITING);
        verify(matchRepository, never()).save(any());
        verify(matchRequestRepository).findOppositeWaitingForUpdate(101L, PageRequest.of(0, 1));
    }

    @Test
    void createsMatchAndMovesBothRequestsToMatchingWhenCandidateExists() {
        VoteQuestion question = question(TODAY);
        VoteAnswer requesterAnswer = answer(101L, requester, question);
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(requesterAnswer));
        when(matchRequestRepository.existsByVoteAnswer_VoteAnswerId(101L)).thenReturn(false);
        when(voteQuestionRepository.findByVoteQuestionIdForUpdate(201L)).thenReturn(Optional.of(question));
        when(matchRequestRepository.saveAndFlush(any(MatchRequest.class)))
                .thenAnswer(invocation -> {
                    MatchRequest request = invocation.getArgument(0);
                    ReflectionTestUtils.setField(request, "matchRequestId", 301L);
                    return request;
                });
        MatchRequest candidate = MatchRequest.waiting(user(12L), mock(VoteAnswer.class));
        when(matchRequestRepository.findOppositeWaitingForUpdate(101L, PageRequest.of(0, 1)))
                .thenReturn(List.of(candidate));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = matchService.createMatchRequest(11L, new MatchRequestCreateRequest(101L));

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match match = matchCaptor.getValue();
        ArgumentCaptor<MatchRequest> requestCaptor = ArgumentCaptor.forClass(MatchRequest.class);
        verify(matchRequestRepository).saveAndFlush(requestCaptor.capture());
        MatchRequest createdRequest = requestCaptor.getValue();

        assertThat(response.data().status()).isEqualTo(MatchRequestStatus.MATCHING);
        assertThat(createdRequest.getStatus()).isEqualTo(MatchRequestStatus.MATCHING);
        assertThat(createdRequest.getMatch()).isSameAs(match);
        assertThat(candidate.getStatus()).isEqualTo(MatchRequestStatus.MATCHING);
        assertThat(candidate.getMatch()).isSameAs(match);
        verify(matchRequestRepository).findOppositeWaitingForUpdate(eq(101L), eq(PageRequest.of(0, 1)));
    }

    @Test
    void translatesConcurrentUniqueViolationToDuplicateMatchRequest() {
        VoteQuestion question = question(TODAY);
        VoteAnswer requesterAnswer = answer(101L, requester, question);
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(requesterAnswer));
        when(matchRequestRepository.existsByVoteAnswer_VoteAnswerId(101L)).thenReturn(false);
        when(voteQuestionRepository.findByVoteQuestionIdForUpdate(201L)).thenReturn(Optional.of(question));
        when(matchRequestRepository.saveAndFlush(any(MatchRequest.class)))
                .thenThrow(new DataIntegrityViolationException("uk_match_requests_vote_answer"));

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS);
    }

    @Test
    void rethrowsUnrelatedIntegrityViolation() {
        VoteQuestion question = question(TODAY);
        VoteAnswer requesterAnswer = answer(101L, requester, question);
        when(voteAnswerRepository.findById(101L)).thenReturn(Optional.of(requesterAnswer));
        when(matchRequestRepository.existsByVoteAnswer_VoteAnswerId(101L)).thenReturn(false);
        when(voteQuestionRepository.findByVoteQuestionIdForUpdate(201L)).thenReturn(Optional.of(question));
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("unrelated_constraint");
        when(matchRequestRepository.saveAndFlush(any(MatchRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isSameAs(exception);
    }

    private User user(Long userId) {
        User user = User.signup("user" + userId + "@example.test", "password-hash", "user" + userId);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private VoteQuestion question(LocalDate voteDate) {
        VoteQuestion voteQuestion = mock(VoteQuestion.class);
        when(voteQuestion.getVoteQuestionId()).thenReturn(201L);
        when(voteQuestion.getVoteDate()).thenReturn(voteDate);
        return voteQuestion;
    }

    private VoteAnswer answer(Long answerId, User user, VoteQuestion voteQuestion) {
        VoteAnswer answer = mock(VoteAnswer.class);
        when(answer.getVoteAnswerId()).thenReturn(answerId);
        when(answer.getUser()).thenReturn(user);
        when(answer.getVoteQuestion()).thenReturn(voteQuestion);
        return answer;
    }
}
