package com.ktb4.team16.mulo.memorysearch.controller;

/** 기존 API와 같은 message/data 성공 응답 래퍼다. */
public record MemorySearchResponse(String message, MemorySearchResult data) { }
