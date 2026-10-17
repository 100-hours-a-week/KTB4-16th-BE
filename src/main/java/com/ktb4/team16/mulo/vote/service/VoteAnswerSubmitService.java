package com.ktb4.team16.mulo.vote.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.dto.request.VoteAnswerSubmitRequest;
import com.ktb4.team16.mulo.vote.dto.response.VoteAnswerSubmitResponse;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import com.ktb4.team16.mulo.vote.exception.VoteAnswerConstraintViolationTranslator;
import com.ktb4.team16.mulo.vote.exception.VoteDomainException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoteAnswerSubmitService {

    private final TodayVoteQuestionService todayVoteQuestionService;
    private final UserRepository userRepository;
    private final VoteAnswerRepository voteAnswerRepository;
    private final VoteQuestionRepository voteQuestionRepository;

    @Transactional
    public VoteAnswerSubmitResponse submit(Long userId, Long voteQuestionId,
            VoteAnswerSubmitRequest request) {
        voteQuestionRepository.findById(voteQuestionId)
                .orElseThrow(() -> new VoteDomainException(ErrorCode.VOTE_NOT_FOUND));
        VoteQuestion assignedQuestion = todayVoteQuestionService.resolveTodayQuestion(userId);
        if (!assignedQuestion.getVoteQuestionId().equals(voteQuestionId)) {
            throw new VoteDomainException(ErrorCode.VOTE_QUESTION_NOT_ASSIGNED);
        }
        if (assignedQuestion.getOptionA().equals(assignedQuestion.getOptionB())) {
            throw new VoteDomainException(ErrorCode.VOTE_OPTIONS_IDENTICAL);
        }

        String answerText = "A".equals(request.selectedOption())
                ? assignedQuestion.getOptionA() : assignedQuestion.getOptionB();
        if (voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(voteQuestionId, userId)
                .isPresent()) {
            throw new VoteDomainException(ErrorCode.VOTE_ALREADY_ANSWERED);
        }

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new VoteDomainException(ErrorCode.UNAUTHORIZED));
        VoteAnswer answer = VoteAnswer.create(user, assignedQuestion, answerText);
        try {
            voteAnswerRepository.saveAndFlush(answer);
        } catch (DataIntegrityViolationException exception) {
            throw VoteAnswerConstraintViolationTranslator.translate(exception);
        }

        VoteAnswerRepository.VoteAnswerCounts counts = voteAnswerRepository.aggregateForQuestion(
                voteQuestionId, assignedQuestion.getOptionA());
        long total = counts.getTotalCount();
        long optionACount = counts.getOptionACount();
        int optionAPercent = total == 0 ? 0 : (int) Math.round(optionACount * 100.0 / total);
        int optionBPercent = total == 0 ? 0 : 100 - optionAPercent;

        return new VoteAnswerSubmitResponse("투표 답변 성공",
                new VoteAnswerSubmitResponse.Data(answer.getVoteAnswerId(), answerText,
                        new VoteAnswerSubmitResponse.Result(optionAPercent, optionBPercent)));
    }
}
