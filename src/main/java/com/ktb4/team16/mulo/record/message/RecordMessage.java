package com.ktb4.team16.mulo.record.message;

public enum RecordMessage {

    MY_PLACE_RECORDS_RETRIEVED("내 자물쇠 목록 조회 성공"),
    RECORD_DETAIL_RETRIEVED("자물쇠 상세 조회 성공"),
    MY_RECORD_REGIONS_RETRIEVED("내 자물쇠 지역 그룹 조회 성공"),
    RECORD_CREATED("자물쇠 생성 성공"),
    RECORD_DELETED("자물쇠가 삭제되었습니다."),
    COMMENT_UPDATED("코멘트 수정 성공");

    private final String message;

    RecordMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
