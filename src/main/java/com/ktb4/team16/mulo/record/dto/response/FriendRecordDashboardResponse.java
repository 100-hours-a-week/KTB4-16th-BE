package com.ktb4.team16.mulo.record.dto.response;

/** 친구 대시보드 API 응답 envelope. */
public record FriendRecordDashboardResponse(
        String message,
        FriendRecordDashboardData data
) {
}
