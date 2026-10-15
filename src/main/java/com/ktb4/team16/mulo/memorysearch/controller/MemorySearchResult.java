package com.ktb4.team16.mulo.memorysearch.controller;

import java.util.List;

/** 건수 없는 AI 문구와 검증된 정확 일치·제안 카드 목록이다. */
public record MemorySearchResult(String answer, List<MemorySearchCard> records,
        List<MemorySearchSuggestedCard> suggestedRecords) { }
