package com.ktb4.team16.mulo.recorddraft.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RecordDraftUpsertRequest(
        @Valid Location location,
        @Valid Music music,
        @Min(value = -50, message = "INVALID_INPUT_VALUE")
        @Max(value = 50, message = "INVALID_INPUT_VALUE")
        Integer moodScore,
        @Size(max = 80, message = "INVALID_INPUT_VALUE") String comment,
        Long uploadId
) {
    public record Location(
            @NotNull(message = "INVALID_INPUT_VALUE")
            @DecimalMin(value = "-90", message = "INVALID_LATITUDE")
            @DecimalMax(value = "90", message = "INVALID_LATITUDE")
            BigDecimal latitude,
            @NotNull(message = "INVALID_INPUT_VALUE")
            @DecimalMin(value = "-180", message = "INVALID_LONGITUDE")
            @DecimalMax(value = "180", message = "INVALID_LONGITUDE")
            BigDecimal longitude,
            @Size(max = 20, message = "INVALID_INPUT_VALUE") String legalDongCode,
            @Size(max = 100, message = "INVALID_INPUT_VALUE") String legalDongName
    ) {
        @AssertTrue(message = "INVALID_INPUT_VALUE")
        public boolean hasMatchingLegalDongFields() {
            return (legalDongCode == null) == (legalDongName == null);
        }
    }

    public record Music(
            @NotBlank(message = "INVALID_INPUT_VALUE")
            @Size(max = 22, message = "INVALID_INPUT_VALUE")
            String externalTrackId,
            @NotBlank(message = "INVALID_INPUT_VALUE")
            @Size(max = 255, message = "INVALID_INPUT_VALUE")
            String title,
            @NotBlank(message = "INVALID_INPUT_VALUE")
            @Size(max = 255, message = "INVALID_INPUT_VALUE")
            String artistName,
            @NotBlank(message = "INVALID_INPUT_VALUE")
            @Size(max = 255, message = "INVALID_INPUT_VALUE")
            String albumImageUrl,
            @NotBlank(message = "INVALID_INPUT_VALUE")
            @Size(max = 255, message = "INVALID_INPUT_VALUE")
            String externalUrl
    ) { }
}
