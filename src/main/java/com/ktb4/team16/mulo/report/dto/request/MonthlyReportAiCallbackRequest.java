package com.ktb4.team16.mulo.report.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.List;

public record MonthlyReportAiCallbackRequest(@NotBlank String jobId, @Min(2026) int year,
        @Min(1) @Max(12) int month, @NotNull OffsetDateTime generatedAt,
        @NotEmpty List<@Valid Result> results) {
    public record Result(@Positive Long userId, @NotNull Status status, @Valid AiRecap aiRecap,
            List<@Valid PhotoScene> photoScenes, String errorCode) { }
    public record AiRecap(@NotBlank String text) { }
    public record PhotoScene(@NotBlank String tag, @Min(0) int count, @Min(0) @Max(100) int ratio) { }
    public enum Status { COMPLETED, FAILED }
}
