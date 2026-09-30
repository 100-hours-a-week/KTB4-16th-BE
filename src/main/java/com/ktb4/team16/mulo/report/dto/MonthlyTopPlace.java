package com.ktb4.team16.mulo.report.dto;

// 월간 대표 법정동에서 선정된 장소 행과 화면에 필요한 법정동 값을 전달한다.
public record MonthlyTopPlace(Long placeId, String legalDongCode, String legalDongName) {
}
