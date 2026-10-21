package com.ktb4.team16.mulo.match.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.dto.request.ChatGroupJoinOrCreateRequest;
import com.ktb4.team16.mulo.match.exception.MatchDomainException;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import com.ktb4.team16.mulo.vote.service.TodayVoteQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DB 조회와 지연 로딩을 끝내 immutable request snapshot을 만든 뒤 트랜잭션을 종료한다. */
@Service
@RequiredArgsConstructor
public class GroupChatEntryRequestFactory {

    private final TodayVoteQuestionService todayVoteQuestionService;
    private final VoteAnswerRepository voteAnswerRepository;

    @Transactional
    public ChatGroupJoinOrCreateRequest prepare(Long userId, Long voteAnswerId) {
        VoteQuestion todayQuestion = todayVoteQuestionService.resolveTodayQuestion(userId);
        VoteAnswer answer = voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(
                        todayQuestion.getVoteQuestionId(), userId)
                .orElseThrow(() -> new MatchDomainException(ErrorCode.GROUP_CHAT_VOTE_REQUIRED));
        if (!answer.getVoteAnswerId().equals(voteAnswerId)) {
            throw new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);
        }
        User user = answer.getUser();
        if (user == null || user.getUserId() == null || !user.getUserId().equals(userId)) {
            throw new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_OWNED);
        }
        if (!todayQuestion.getVoteQuestionId().equals(answer.getVoteQuestion().getVoteQuestionId())) {
            throw new MatchDomainException(ErrorCode.MATCH_VOTE_ANSWER_NOT_TODAY);
        }

        String voteChoice;
        if (todayQuestion.getOptionA().equals(answer.getSelectedOption())) {
            voteChoice = "A";
        } else if (todayQuestion.getOptionB().equals(answer.getSelectedOption())) {
            voteChoice = "B";
        } else {
            throw new MatchDomainException(ErrorCode.GROUP_CHAT_VOTE_REQUIRED);
        }

        return new ChatGroupJoinOrCreateRequest(todayQuestion.getVoteDate(),
                todayQuestion.getVoteQuestionId(), userId, user.getNickname(), voteChoice);
    }
}
