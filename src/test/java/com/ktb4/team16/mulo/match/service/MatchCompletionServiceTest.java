package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.match.domain.MatchRequestStatus;
import com.ktb4.team16.mulo.match.entity.Match;
import com.ktb4.team16.mulo.match.entity.MatchRequest;
import com.ktb4.team16.mulo.match.repository.MatchRepository;
import com.ktb4.team16.mulo.match.repository.MatchRequestRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class MatchCompletionServiceTest {

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchRequestRepository requestRepository = mock(MatchRequestRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T15:01:00Z"), ZoneId.of("Asia/Seoul"));
    private final MatchCompletionService service = new MatchCompletionService(matchRepository, requestRepository, clock);

    private Match match;
    private MatchRequest first;
    private MatchRequest second;

    @BeforeEach
    void setUp() {
        match = Match.create(mock(VoteQuestion.class));
        ReflectionTestUtils.setField(match, "matchId", 81L);
        first = matchingRequest(match, 11L);
        second = matchingRequest(match, 12L);
        when(matchRepository.findByMatchIdForUpdate(81L)).thenReturn(Optional.of(match));
        when(requestRepository.findByMatchIdForUpdate(81L)).thenReturn(List.of(first, second));
    }

    @Test
    void completesBothParticipantsAndSavesChatRoomAtomicallyInTheTransaction() {
        service.complete(81L, 701L);

        assertThat(match.getChatRoomId()).isEqualTo(701L);
        assertThat(match.getMatchedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 18, 0, 1));
        assertThat(first.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
        assertThat(second.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);

        service.complete(81L, 701L);
        assertThat(first.getStatus()).isEqualTo(MatchRequestStatus.MATCHED);
    }

    @Test
    void rejectsDifferentChatRoomIdWithoutOverwritingTheCompletedMatch() {
        service.complete(81L, 701L);

        assertThatThrownBy(() -> service.complete(81L, 702L)).isInstanceOf(IllegalStateException.class);
        assertThat(match.getChatRoomId()).isEqualTo(701L);
    }

    @Test
    void failureIsTerminalButCannotOverwriteACompletedMatch() {
        service.fail(81L);
        assertThat(first.getStatus()).isEqualTo(MatchRequestStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(MatchRequestStatus.FAILED);

        Match completed = Match.create(mock(VoteQuestion.class));
        ReflectionTestUtils.setField(completed, "matchId", 82L);
        completed.completeChatRoom(702L, java.time.LocalDateTime.now(clock));
        when(matchRepository.findByMatchIdForUpdate(82L)).thenReturn(Optional.of(completed));
        service.fail(82L);
        assertThat(completed.getChatRoomId()).isEqualTo(702L);
    }

    @Test
    void completionAndFailureAlwaysStartIndependentTransactions() throws NoSuchMethodException {
        Method complete = MatchCompletionService.class.getMethod("complete", Long.class, Long.class);
        Method fail = MatchCompletionService.class.getMethod("fail", Long.class);

        assertThat(complete.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(fail.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private MatchRequest matchingRequest(Match match, Long userId) {
        User user = User.signup("user" + userId + "@example.test", "password-hash", "user" + userId);
        ReflectionTestUtils.setField(user, "userId", userId);
        MatchRequest request = MatchRequest.waiting(user, mock(VoteAnswer.class));
        request.startMatching(match);
        return request;
    }
}
