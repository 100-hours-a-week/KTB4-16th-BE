package com.ktb4.team16.mulo.record.dto.response;

import java.util.List;

public record RecordRegionsResponse(
        String message,
        List<RecordRegionGroupResponse> data
) {
}
