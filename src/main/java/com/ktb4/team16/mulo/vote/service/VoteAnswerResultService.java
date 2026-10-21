package com.ktb4.team16.mulo.vote.service;

import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.vote.dto.response.VoteAnswerSubmitResponse;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoteAnswerResultService {
    private final VoteAnswerRepository voteAnswerRepository;

    public VoteAnswerSubmitResponse.Result getResult(VoteQuestion question) {
        VoteAnswerRepository.VoteAnswerCounts counts = voteAnswerRepository.aggregateForQuestion(
                question.getVoteQuestionId(), question.getOptionA());
        long total = counts.getTotalCount();
        long optionACount = counts.getOptionACount();
        int optionAPercent = total == 0 ? 0 : (int) Math.round(optionACount * 100.0 / total);
        int optionBPercent = total == 0 ? 0 : 100 - optionAPercent;
        return new VoteAnswerSubmitResponse.Result(optionAPercent, optionBPercent);
    }
}
