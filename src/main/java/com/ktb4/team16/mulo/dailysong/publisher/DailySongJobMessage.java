package com.ktb4.team16.mulo.dailysong.publisher;

public record DailySongJobMessage(long jobId) {
    public DailySongJobMessage {
        if (jobId <= 0) {
            throw new IllegalArgumentException("오늘의 노래 작업 ID는 양수여야 합니다.");
        }
    }
}
