package com.ktb4.team16.mulo.record.controller;

import com.ktb4.team16.mulo.record.dto.response.RecordRegionsResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsResponse;
import com.ktb4.team16.mulo.record.message.RecordMessage;
import com.ktb4.team16.mulo.record.service.RecordService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Validated
public class RecordController {

    private final RecordService recordService;

    @GetMapping
    public RecordRegionRecordsResponse getMyRecords(
            @AuthenticationPrincipal Long userId,
            @RequestParam @NotBlank String legalDongCode,
            @RequestParam(required = false) String cursor
    ) {
        return new RecordRegionRecordsResponse(
                RecordMessage.MY_PLACE_RECORDS_RETRIEVED.message(),
                recordService.getMyRecords(userId, legalDongCode, cursor)
        );
    }

    @GetMapping("/regions")
    public RecordRegionsResponse getMyRecordRegions(
            @AuthenticationPrincipal Long userId
    ) {
        return new RecordRegionsResponse(
                RecordMessage.MY_RECORD_REGIONS_RETRIEVED.message(),
                recordService.getMyRecordRegions(userId)
        );
    }
}
