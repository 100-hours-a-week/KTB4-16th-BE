package com.ktb4.team16.mulo.report.batch;

/** 운영자가 구분할 수 있는 배치 충돌·접수 확인 필요 사유를 전달한다. */
public class ReportBatchException extends RuntimeException {
    private final String code;

    /** 외부에 원본 AI 오류를 노출하지 않고 업무 사유만 저장한다. */
    public ReportBatchException(String code) {
        super(code);
        this.code = code;
    }

    /** API와 Batch ExitStatus가 같은 업무 사유를 사용한다. */
    public String code() {
        return code;
    }
}
