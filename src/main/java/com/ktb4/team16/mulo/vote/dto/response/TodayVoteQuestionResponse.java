package com.ktb4.team16.mulo.vote.dto.response;

public record TodayVoteQuestionResponse(String message, Data data) {

    public record Data(
            Long voteQuestionId,
            String question,
            String optionA,
            String optionB,
            String myAnswer,
            Long voteAnswerId
    ) { }
}
