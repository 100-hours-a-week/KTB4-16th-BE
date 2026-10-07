package com.ktb4.team16.mulo.record.dto.response;

import java.util.List;

/** 친구 프로필 정보와 지역별 활성 자물쇠 요약을 묶는다. */
public record FriendRecordDashboardData(
        Long userId,
        String nickname,
        Long recordsCount,
        List<RecordRegionGroupResponse> regions
) {
}
