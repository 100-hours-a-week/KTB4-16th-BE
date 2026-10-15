package com.ktb4.team16.mulo.memorysearch.controller;

import com.ktb4.team16.mulo.memorysearch.MemorySearchLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 프론트가 보내는 대화 ID와 현재 질문만 받는다. */
public record MemorySearchRequest(@NotBlank String conversationId,
        @NotBlank @Size(max = MemorySearchLimits.MAX_QUERY_LENGTH) String query) { }
