package com.ktb4.team16.mulo.place.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PopularTracksSearchRequest(
        @NotEmpty(message = "PLACE_IDS_REQUIRED")
        List<
                @NotNull(message = "INVALID_PLACE_ID")
                @Positive(message = "INVALID_PLACE_ID")
                        Long
                > placeIds
) {
}
