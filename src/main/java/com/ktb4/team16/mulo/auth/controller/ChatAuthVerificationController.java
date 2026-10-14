package com.ktb4.team16.mulo.auth.controller;

import com.ktb4.team16.mulo.auth.dto.response.ChatAuthVerifyResponse;
import com.ktb4.team16.mulo.auth.service.ChatAuthVerificationService;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class ChatAuthVerificationController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final ChatAuthVerificationService chatAuthVerificationService;

    @PostMapping("/verify")
    public ChatAuthVerifyResponse verify(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @AuthenticationPrincipal Long userId) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || authorization.substring(BEARER_PREFIX.length()).isBlank()) {
            throw new UnauthenticatedUserException();
        }
        return chatAuthVerificationService.verify(userId, authorization.substring(BEARER_PREFIX.length()));
    }
}
