package com.ktb4.team16.mulo.vote.dto.response;

public record VoteAnswerSubmitResponse(String message, Data data) {

    public record Data(Long voteAnswerId, String myAnswer, Result result) { }

    public record Result(int optionAPercent, int optionBPercent) { }
}
