package com.ktb4.team16.mulo.recorddraft.message;

public enum RecordDraftMessage {
    RECORD_DRAFT_RETRIEVED("임시저장 조회 성공"),
    RECORD_DRAFT_SAVED("임시저장 저장 성공");

    private final String message;

    RecordDraftMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
