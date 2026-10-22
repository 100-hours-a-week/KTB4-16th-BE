package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        matchService = serviceAt(LocalDate.of(2026, 10, 22), LocalTime.NOON);
        requester = user(11L);
    }

    @Test
    void returnsCurrentStatusForOwnedMatchRequestsIncludingExistingDirectMatch() {
        MatchRequest waiting = matchRequest(301L, MatchRequestStatus.WAITING, null, true);
        MatchRequest matching = matchRequest(302L, MatchRequestStatus.MATCHING, null, true);
        MatchRequest expired = matchRequest(303L, MatchRequestStatus.EXPIRED, null, true);
        MatchRequest failed = matchRequest(304L, MatchRequestStatus.FAILED, null, true);
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(301L, 11L))
                .thenReturn(Optional.of(waiting));
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(302L, 11L))
                .thenReturn(Optional.of(matching));
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(303L, 11L))
                .thenReturn(Optional.of(expired));
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(304L, 11L))
                .thenReturn(Optional.of(failed));

        assertThat(matchService.getMatchRequestStatus(11L, 301L).data().status())
                .isEqualTo(MatchRequestStatus.WAITING);
        assertThat(matchService.getMatchRequestStatus(11L, 302L).data().status())
                .isEqualTo(MatchRequestStatus.MATCHING);
        assertThat(matchService.getMatchRequestStatus(11L, 303L).data().status())
                .isEqualTo(MatchRequestStatus.EXPIRED);
        assertThat(matchService.getMatchRequestStatus(11L, 304L).data().status())
                .isEqualTo(MatchRequestStatus.FAILED);
    }

    @Test
    void returnsMatchedDirectRoomIdForExistingRequest() {
        MatchRequest matched = matchRequest(304L, MatchRequestStatus.MATCHED, 901L, true);
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(304L, 11L))
                .thenReturn(Optional.of(matched));

        var response = matchService.getMatchRequestStatus(11L, 304L);

        assertThat(response.data().matchRequestId()).isEqualTo(304L);
        assertThat(response.data().status()).isEqualTo(MatchRequestStatus.MATCHED);
        assertThat(response.data().chatRoomId()).isEqualTo(901L);
    }

    @Test
    void returnsMatchRequestStatusByOwnedVoteAnswer() {
        MatchRequest failed = matchRequest(305L, MatchRequestStatus.FAILED, null, true);
        when(matchRequestRepository.findByVoteAnswerIdAndUserId(101L, 11L))
                .thenReturn(Optional.of(failed));

        var response = matchService.getMatchRequestStatusByVoteAnswer(11L, 101L);

        assertThat(response.data().matchRequestId()).isEqualTo(305L);
        assertThat(response.data().status()).isEqualTo(MatchRequestStatus.FAILED);
        assertThat(response.data().chatRoomId()).isNull();
    }

    @Test
    void returnsNotFoundWhenVoteAnswerHasNoOwnedMatchRequest() {
        when(matchRequestRepository.findByVoteAnswerIdAndUserId(101L, 11L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatchRequestStatusByVoteAnswer(11L, 101L))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_REQUEST_NOT_FOUND);
    }

    @Test
    void hidesOtherUsersExistingRequests() {
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(405L, 11L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatchRequestStatus(11L, 405L))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.MATCH_REQUEST_NOT_FOUND);
        verify(matchRequestRepository).findByMatchRequestIdAndUser_UserId(405L, 11L);
    }

    @Test
    void rejectsMatchedRequestWithoutMatchOrChatRoomIdAsInternalInvariantViolation() {
        MatchRequest withoutMatch = matchRequest(306L, MatchRequestStatus.MATCHED, null, false);
        MatchRequest withoutChatRoom = matchRequest(307L, MatchRequestStatus.MATCHED, null, true);
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(306L, 11L))
                .thenReturn(Optional.of(withoutMatch));
        when(matchRequestRepository.findByMatchRequestIdAndUser_UserId(307L, 11L))
                .thenReturn(Optional.of(withoutChatRoom));

        assertThatThrownBy(() -> matchService.getMatchRequestStatus(11L, 306L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> matchService.getMatchRequestStatus(11L, 307L))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-20, 12:00:00",
            "2026-10-21, 00:00:00",
            "2026-10-22, 00:00:00",
            "2026-10-22, 22:59:59",
            "2026-10-22, 23:00:00"
    })
    void rejectsNewOneToOneRequestsRegardlessOfKstDateOrTime(LocalDate date, LocalTime time) {
        matchService = serviceAt(date, time);

        assertThatThrownBy(() -> matchService.createMatchRequest(
                11L, new MatchRequestCreateRequest(101L)))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(ErrorCode.ONE_TO_ONE_MATCHING_REPLACED);

        verifyNoInteractions(matchRepository, matchRequestRepository,
                voteAnswerRepository, voteQuestionRepository);
    }

    private User user(Long userId) {
        User user = User.signup("user" + userId + "@example.test", "password-hash", "user" + userId);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private MatchRequest matchRequest(
            Long matchRequestId,
            MatchRequestStatus status,
            Long chatRoomId,
            boolean attachMatch
    ) {
        MatchRequest matchRequest = MatchRequest.waiting(requester, mock(VoteAnswer.class));
        ReflectionTestUtils.setField(matchRequest, "matchRequestId", matchRequestId);
        if (status == MatchRequestStatus.EXPIRED) {
            matchRequest.expire();
        } else if (status == MatchRequestStatus.MATCHING
                || (status == MatchRequestStatus.MATCHED && attachMatch)
                || status == MatchRequestStatus.FAILED) {
            Match match = Match.create(mock(VoteQuestion.class));
            ReflectionTestUtils.setField(match, "chatRoomId", chatRoomId);
            matchRequest.startMatching(match);
            if (status == MatchRequestStatus.MATCHED) {
                ReflectionTestUtils.setField(matchRequest, "status", MatchRequestStatus.MATCHED);
            } else if (status == MatchRequestStatus.FAILED) {
                matchRequest.failMatching();
            }
        } else if (status == MatchRequestStatus.MATCHED) {
            ReflectionTestUtils.setField(matchRequest, "status", MatchRequestStatus.MATCHED);
        }
        return matchRequest;
    }

    private MatchService serviceAt(LocalDate date, LocalTime time) {
        Clock clock = Clock.fixed(date.atTime(time).atZone(KST).toInstant(), KST);
        return new MatchService(
                matchRepository, matchRequestRepository, voteAnswerRepository, voteQuestionRepository, clock);
    }
}
