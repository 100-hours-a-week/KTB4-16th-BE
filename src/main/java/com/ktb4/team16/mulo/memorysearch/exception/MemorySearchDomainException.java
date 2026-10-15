package com.ktb4.team16.mulo.memorysearch.exception;

import com.ktb4.team16.mulo.global.error.ErrorCode;

/** 기억 검색 상태 오류를 공개 오류 코드와 함께 전달한다. */
public class MemorySearchDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 공개 상태와 메시지의 단일 출처로 오류 코드를 보관한다. */
    public MemorySearchDomainException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /** 공통 예외 처리기가 응답에 사용할 공개 오류 코드를 반환한다. */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
