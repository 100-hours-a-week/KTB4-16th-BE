package com.ktb4.team16.mulo.vote.ai;

import java.time.LocalDate;

public record AiVoteQuestion(
        LocalDate questionDate,
        String musicGenre,
        String question,
        String optionA,
        String optionB
) { }
