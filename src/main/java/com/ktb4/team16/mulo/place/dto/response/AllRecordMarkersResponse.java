package com.ktb4.team16.mulo.place.dto.response;

import java.util.List;

public record AllRecordMarkersResponse(
        String message,
        List<AllRecordMarkerResponse> data
) {
}
