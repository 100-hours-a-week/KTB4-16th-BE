package com.ktb4.team16.mulo.record.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MyPlaceRecordsSearchRequest(
        @NotEmpty(message = "PLACE_IDS_REQUIRED")
        List<
                @NotNull(message = "INVALID_PLACE_ID")
                @Positive(message = "INVALID_PLACE_ID")
                        Long
                > placeIds,
        String cursor
) {
}
