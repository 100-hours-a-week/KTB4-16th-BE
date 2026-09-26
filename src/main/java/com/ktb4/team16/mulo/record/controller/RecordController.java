package com.ktb4.team16.mulo.record.controller;

import com.ktb4.team16.mulo.record.dto.request.RecordCreateRequest;
import com.ktb4.team16.mulo.record.dto.request.RecordCommentUpdateRequest;
import com.ktb4.team16.mulo.record.dto.response.RecordCommentUpdateResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordCreateResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordDetailData;
import com.ktb4.team16.mulo.record.dto.response.RecordDetailResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionRecordsResponse;
import com.ktb4.team16.mulo.record.dto.response.RecordRegionsResponse;
import com.ktb4.team16.mulo.record.message.RecordMessage;
import com.ktb4.team16.mulo.record.service.RecordService;
import com.ktb4.team16.mulo.record.exception.MissingCommentFieldException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Validated
public class RecordController {

    private final RecordService recordService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecordCreateApiResponse createRecord(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RecordCreateRequest request
    ) {
        RecordCreateResponse record = recordService.createRecord(userId, request);
        return new RecordCreateApiResponse(
                RecordMessage.RECORD_CREATED.message(),
                record
        );
    }

    @GetMapping("/{recordId}")
    public RecordDetailResponse getRecordDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId
    ) {
        RecordDetailData recordDetail = recordService.getRecordDetail(userId, recordId);
        return new RecordDetailResponse(
                RecordMessage.RECORD_DETAIL_RETRIEVED.message(),
                recordDetail
        );
    }

    @PatchMapping("/{recordId}/comment")
    public RecordCommentUpdateApiResponse updateComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long recordId,
            @Valid @RequestBody RecordCommentUpdateRequest request
    ) {
        if (!request.hasCommentField()) {
            throw new MissingCommentFieldException();
        }

        RecordCommentUpdateResponse response = recordService.updateRecordComment(
                userId,
                recordId,
                request.getComment());
        return new RecordCommentUpdateApiResponse(
                RecordMessage.COMMENT_UPDATED.message(),
                response
        );
    }

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
