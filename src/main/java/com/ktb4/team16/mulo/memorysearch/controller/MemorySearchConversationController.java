package com.ktb4.team16.mulo.memorysearch.controller;

import com.ktb4.team16.mulo.memorysearch.service.MemorySearchConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 기억 검색 화면의 대화 시작·종료 REST 경계를 제공한다. */
@RestController
@RequestMapping("/api/memories/search/conversations")
@RequiredArgsConstructor
public class MemorySearchConversationController {

    private final MemorySearchConversationService service;

    /** 인증 사용자에게 새 대화 ID를 발급해 201 응답으로 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemorySearchConversationStartedResponse start(
            @AuthenticationPrincipal Long userId) {
        String conversationId = service.start(userId);
        return new MemorySearchConversationStartedResponse("대화 시작 성공",
                new MemorySearchConversationStartedResponse.Data(conversationId));
    }

    /** 명시적 나가기로 해당 사용자의 대화만 삭제하고 204를 반환한다. */
    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@AuthenticationPrincipal Long userId,
            @PathVariable String conversationId) {
        service.close(userId, conversationId);
    }
}
