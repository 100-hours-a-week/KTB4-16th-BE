package com.ktb4.team16.mulo.record.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecordCreateRequest(
        @Valid @NotNull(message = "INVALID_INPUT_VALUE") Location location,
        @Valid @NotNull(message = "INVALID_INPUT_VALUE") Music music,
        @NotNull(message = "INVALID_INPUT_VALUE") Integer moodScore,
        String comment,
        @NotNull(message = "INVALID_INPUT_VALUE") Long uploadId
) {
    public record Location(
            @NotNull(message = "INVALID_INPUT_VALUE") BigDecimal latitude,
            @NotNull(message = "INVALID_INPUT_VALUE") BigDecimal longitude,
            String legalDongCode,
            String legalDongName
    ) { }

    public record Music(
            @NotBlank(message = "INVALID_INPUT_VALUE") String externalTrackId
    ) { }
}
