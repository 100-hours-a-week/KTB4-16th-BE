package com.ktb4.team16.mulo.report.dto;

// 월간 스냅샷 생성에 필요한 기록 수와 평균 기분 집계 결과를 전달한다.
public record MonthlyRecordSummary(Long recordCount, Double averageMoodScore) {
}
