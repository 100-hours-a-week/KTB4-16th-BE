package com.ktb4.team16.mulo.dailysong.controller;

import com.ktb4.team16.mulo.dailysong.dto.DailySongDetailResponse;
import com.ktb4.team16.mulo.dailysong.service.DailySongQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-songs")
@RequiredArgsConstructor
public class DailySongController {
    private final DailySongQueryService dailySongQueryService;

    @GetMapping("/{jobId}")
    public DailySongDetailResponse getDailySong(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long jobId
    ) {
        return dailySongQueryService.getDailySong(userId, jobId);
    }
}
