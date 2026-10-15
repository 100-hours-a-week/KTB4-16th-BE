package com.ktb4.team16.mulo.memorysearch.controller;

import jakarta.validation.constraints.NotBlank;

/** 프론트가 보내는 대화 ID와 현재 질문만 받는다. */
public record MemorySearchRequest(@NotBlank String conversationId,
        @NotBlank String query) { }
