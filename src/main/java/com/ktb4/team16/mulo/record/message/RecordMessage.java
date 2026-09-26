package com.ktb4.team16.mulo.record.message;

public enum RecordMessage {

    MY_PLACE_RECORDS_RETRIEVED("내 자물쇠 목록 조회 성공"),
    RECORD_DETAIL_RETRIEVED("자물쇠 상세 조회 성공"),
    RECORD_CREATED("자물쇠 생성 성공");

    private final String message;

    RecordMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
