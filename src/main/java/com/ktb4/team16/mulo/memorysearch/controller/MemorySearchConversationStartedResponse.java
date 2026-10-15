package com.ktb4.team16.mulo.memorysearch.controller;

/** 새 대화 식별자를 기존 message/data 공개 응답 구조로 전달한다. */
public record MemorySearchConversationStartedResponse(String message, Data data) {

    /** 사용자 질문에 포함할 서버 발급 대화 ID만 공개한다. */
    public record Data(String conversationId) {
    }
}
