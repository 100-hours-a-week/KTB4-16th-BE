package com.ktb4.team16.mulo.record.controller;

import com.ktb4.team16.mulo.record.dto.response.FriendRecordDashboardData;
import com.ktb4.team16.mulo.record.dto.response.FriendRecordDashboardResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsResponse;
import com.ktb4.team16.mulo.record.message.RecordMessage;
import com.ktb4.team16.mulo.record.service.FriendRecordReadService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 친구 대시보드와 친구 지역 자물쇠 조회 요청을 읽기 서비스에 연결한다. */
@RestController
@RequestMapping("/api/users/{friendUserId}/records")
@RequiredArgsConstructor
@Validated
public class FriendRecordController {

    private final FriendRecordReadService friendRecordReadService;

    /** 현재 친구 관계가 있는 사용자의 지역 요약과 총 자물쇠 수를 반환한다. */
    @GetMapping("/regions")
    public FriendRecordDashboardResponse getFriendRecordRegions(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long friendUserId
    ) {
        FriendRecordDashboardData dashboard = friendRecordReadService.getDashboard(
                viewerId, friendUserId);
        return new FriendRecordDashboardResponse(
                RecordMessage.FRIEND_RECORD_REGIONS_RETRIEVED.message(), dashboard);
    }

    /** 현재 친구 관계를 재검증하고 지정 지역의 자물쇠 커서 페이지를 반환한다. */
    @GetMapping
    public RecordRegionRecordsResponse getFriendRecords(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long friendUserId,
            @RequestParam @NotBlank String legalDongCode,
            @RequestParam(required = false) String cursor
    ) {
        return new RecordRegionRecordsResponse(
                RecordMessage.FRIEND_RECORDS_RETRIEVED.message(),
                friendRecordReadService.getRegionRecords(
                        viewerId, friendUserId, legalDongCode, cursor));
    }
}
