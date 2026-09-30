package com.ktb4.team16.mulo.report.dto;

// 월간 기록 수·최근 시각·ID 순으로 선정된 장소와 응답에 필요한 법정동 값을 전달한다.
public record MonthlyTopPlace(Long placeId, String legalDongCode, String legalDongName) {
}
