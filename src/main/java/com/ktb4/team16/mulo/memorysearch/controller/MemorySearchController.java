package com.ktb4.team16.mulo.memorysearch.controller;

import com.ktb4.team16.mulo.memorysearch.service.MemorySearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자의 기억 검색 질문을 REST 요청으로 받는다. */
@RestController
@RequestMapping("/api/memories/search")
@RequiredArgsConstructor
public class MemorySearchController {

    private final MemorySearchService service;

    /** 대화 ID와 질문을 검증하고 검색 결과를 공개 응답 구조로 감싼다. */
    @PostMapping
    public MemorySearchResponse search(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody MemorySearchRequest request) {
        MemorySearchResult result = service.search(userId, request.conversationId(),
                request.query().strip());
        return new MemorySearchResponse("기억 검색 답변 생성 성공", result);
    }
}
