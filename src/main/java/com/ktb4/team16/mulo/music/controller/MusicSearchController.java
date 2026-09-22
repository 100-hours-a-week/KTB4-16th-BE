package com.ktb4.team16.mulo.music.controller;

import com.ktb4.team16.mulo.music.dto.MusicSearchResponse;
import com.ktb4.team16.mulo.music.service.MusicSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicSearchController {
    private final MusicSearchService musicSearchService;

    // 인증 사용자와 검색어를 서비스에 전달하고 공개 응답 DTO를 반환한다.
    @GetMapping("/search")
    public MusicSearchResponse search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String q) {
        // 중요: 인증 필터가 검증한 userId를 사용자별 호출 제한의 식별자로 사용한다.
        return MusicSearchResponse.from(musicSearchService.search(userId, q));
    }
}
