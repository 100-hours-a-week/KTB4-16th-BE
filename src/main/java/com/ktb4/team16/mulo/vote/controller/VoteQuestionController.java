package com.ktb4.team16.mulo.vote.controller;

import com.ktb4.team16.mulo.vote.dto.response.TodayVoteQuestionResponse;
import com.ktb4.team16.mulo.vote.service.TodayVoteQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vote-questions")
@RequiredArgsConstructor
public class VoteQuestionController {

    private final TodayVoteQuestionService todayVoteQuestionService;

    @GetMapping("/today")
    public TodayVoteQuestionResponse getToday(@AuthenticationPrincipal Long userId) {
        return todayVoteQuestionService.getToday(userId);
    }
}
