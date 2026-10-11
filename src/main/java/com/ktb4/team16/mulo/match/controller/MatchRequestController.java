package com.ktb4.team16.mulo.match.controller;

import com.ktb4.team16.mulo.match.dto.request.MatchRequestCreateRequest;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestCreateResponse;
import com.ktb4.team16.mulo.match.dto.response.MatchRequestStatusResponse;
import com.ktb4.team16.mulo.match.service.MatchService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/match-requests")
@RequiredArgsConstructor
public class MatchRequestController {

    private final MatchService matchService;

    @GetMapping("/{matchRequestId}")
    public MatchRequestStatusResponse getMatchRequestStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long matchRequestId
    ) {
        return matchService.getMatchRequestStatus(userId, matchRequestId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchRequestCreateResponse createMatchRequest(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MatchRequestCreateRequest request
    ) {
        return matchService.createMatchRequest(userId, request);
    }
}
