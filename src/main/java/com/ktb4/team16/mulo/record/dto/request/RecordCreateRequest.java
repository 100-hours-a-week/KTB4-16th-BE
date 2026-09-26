package com.ktb4.team16.mulo.record.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RecordCreateRequest(
        @Valid @NotNull(message = "INVALID_INPUT_VALUE") Location location,
        @Valid @NotNull(message = "INVALID_INPUT_VALUE") Music music,
        @NotNull(message = "INVALID_INPUT_VALUE")
        @Min(value = -50, message = "INVALID_INPUT_VALUE")
        @Max(value = 50, message = "INVALID_INPUT_VALUE")
        Integer moodScore,
        @Size(max = 80, message = "INVALID_INPUT_VALUE") String comment,
        @NotNull(message = "INVALID_INPUT_VALUE") Long uploadId
) {
    public record Location(
            @NotNull(message = "INVALID_INPUT_VALUE") BigDecimal latitude,
            @NotNull(message = "INVALID_INPUT_VALUE") BigDecimal longitude,
            String legalDongCode,
            String legalDongName
    ) {
        @AssertTrue(message = "INVALID_INPUT_VALUE")
        public boolean hasMatchingLegalDongFields() {
            return (legalDongCode == null) == (legalDongName == null);
        }
    }

    public record Music(
            @NotBlank(message = "INVALID_INPUT_VALUE") String externalTrackId,
            @NotBlank(message = "INVALID_INPUT_VALUE") String title,
            @NotBlank(message = "INVALID_INPUT_VALUE") String artistName,
            @NotBlank(message = "INVALID_INPUT_VALUE") String albumImageUrl,
            @NotBlank(message = "INVALID_INPUT_VALUE") String externalUrl
    ) { }
}
