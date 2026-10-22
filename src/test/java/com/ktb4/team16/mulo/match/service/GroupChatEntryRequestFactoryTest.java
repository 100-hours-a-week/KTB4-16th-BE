package com.ktb4.team16.mulo.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import com.ktb4.team16.mulo.vote.service.TodayVoteQuestionService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GroupChatEntryRequestFactoryTest {
    private final TodayVoteQuestionService todayVoteQuestionService = mock(TodayVoteQuestionService.class);
    private final VoteAnswerRepository voteAnswerRepository = mock(VoteAnswerRepository.class);
    private final GroupChatEntryRequestFactory factory =
            new GroupChatEntryRequestFactory(todayVoteQuestionService, voteAnswerRepository);

    @Test
    void derivesGroupJoinPayloadFromAuthenticatedUsersTodayAnswer() {
        VoteQuestion question = question();
        VoteAnswer answer = answer(question, 101L, 9L, "Choice B");
        when(todayVoteQuestionService.resolveTodayQuestion(9L)).thenReturn(question);
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(44L, 9L))
                .thenReturn(Optional.of(answer));

        ChatGroupJoinOrCreateRequest request = factory.prepare(9L, 101L);

        assertThat(request).isEqualTo(new ChatGroupJoinOrCreateRequest(
                LocalDate.of(2026, 10, 21), 44L, 9L, "trusted nickname", "B"));
    }

    @Test
    void rejectsUserWhoHasNotAnsweredToday() {
        VoteQuestion question = question();
        when(todayVoteQuestionService.resolveTodayQuestion(9L)).thenReturn(question);
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(44L, 9L))
                .thenReturn(Optional.empty());

        assertError(9L, 101L, ErrorCode.GROUP_CHAT_VOTE_REQUIRED);
    }

    @Test
    void rejectsClientSuppliedAnswerIdThatDoesNotMatchAuthenticatedUsersTodayAnswer() {
        VoteQuestion question = question();
        when(todayVoteQuestionService.resolveTodayQuestion(9L)).thenReturn(question);
        VoteAnswer answer = answer(question, 101L, 9L, "Choice A");
        when(voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(44L, 9L))
                .thenReturn(Optional.of(answer));

        assertError(9L, 202L, ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);
    }

    private VoteQuestion question() {
        VoteQuestion question = mock(VoteQuestion.class);
        when(question.getVoteQuestionId()).thenReturn(44L);
        when(question.getVoteDate()).thenReturn(LocalDate.of(2026, 10, 21));
        when(question.getOptionA()).thenReturn("Choice A");
        when(question.getOptionB()).thenReturn("Choice B");
        return question;
    }

    private VoteAnswer answer(VoteQuestion question, Long answerId, Long userId, String selectedOption) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getNickname()).thenReturn("trusted nickname");
        VoteAnswer answer = mock(VoteAnswer.class);
        when(answer.getVoteAnswerId()).thenReturn(answerId);
        when(answer.getUser()).thenReturn(user);
        when(answer.getVoteQuestion()).thenReturn(question);
        when(answer.getSelectedOption()).thenReturn(selectedOption);
        return answer;
    }

    private void assertError(Long userId, Long answerId, ErrorCode expected) {
        assertThatThrownBy(() -> factory.prepare(userId, answerId))
                .isInstanceOf(MatchDomainException.class)
                .extracting(exception -> ((MatchDomainException) exception).errorCode())
                .isEqualTo(expected);
    }
}
