package com.ktb4.team16.mulo.vote.controller;

import com.ktb4.team16.mulo.vote.dto.request.VoteAnswerSubmitRequest;
import com.ktb4.team16.mulo.vote.dto.response.TodayVoteQuestionResponse;
import com.ktb4.team16.mulo.vote.dto.response.VoteAnswerSubmitResponse;
import com.ktb4.team16.mulo.vote.service.TodayVoteQuestionService;
import com.ktb4.team16.mulo.vote.service.VoteAnswerSubmitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vote-questions")
@RequiredArgsConstructor
public class VoteQuestionController {

    private final TodayVoteQuestionService todayVoteQuestionService;
    private final VoteAnswerSubmitService voteAnswerSubmitService;

    @GetMapping("/today")
    public TodayVoteQuestionResponse getToday(@AuthenticationPrincipal Long userId) {
        return todayVoteQuestionService.getToday(userId);
    }

    @PostMapping("/{voteQuestionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public VoteAnswerSubmitResponse submitAnswer(@AuthenticationPrincipal Long userId,
            @PathVariable @Positive(message = "INVALID_INPUT_VALUE") Long voteQuestionId,
            @Valid @RequestBody VoteAnswerSubmitRequest request) {
        return voteAnswerSubmitService.submit(userId, voteQuestionId, request);
    }
}
