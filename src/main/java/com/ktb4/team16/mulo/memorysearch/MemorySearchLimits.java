package com.ktb4.team16.mulo.memorysearch;

/** 기억 검색의 제품 계약에서 정한 턴·정확 일치 결과 건수 상한이다. */
public final class MemorySearchLimits {

    /** 다음 AI 요청에 전달할 완료된 이전 질문의 최대 개수다. */
    public static final int MAX_RECENT_TURNS = 5;

    /** 한 번의 정확 일치 검색에서 공개·문맥 저장할 자물쇠 ID의 최대 개수다. */
    public static final int MAX_RESULT_RECORDS = 50;

    /** AI가 일부 조건만 일치한다고 제안할 수 있는 자물쇠의 최대 개수다. */
    public static final int MAX_SUGGESTED_RECORDS = 3;

    /** 프론트와 공개 질문 API에서 허용하는 질문의 최대 글자 수다. */
    public static final int MAX_QUERY_LENGTH = 500;

    /** 상수만 제공하므로 인스턴스를 만들지 않는다. */
    private MemorySearchLimits() { }
}
