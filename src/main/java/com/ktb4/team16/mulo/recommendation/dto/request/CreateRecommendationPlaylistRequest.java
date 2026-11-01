package com.ktb4.team16.mulo.recommendation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRecommendationPlaylistRequest(
        @NotNull(message = "LATITUDE_REQUIRED")
        @DecimalMin(value = "-90.0", message = "INVALID_LATITUDE")
        @DecimalMax(value = "90.0", message = "INVALID_LATITUDE") Double latitude,
        @NotNull(message = "LONGITUDE_REQUIRED")
        @DecimalMin(value = "-180.0", message = "INVALID_LONGITUDE")
        @DecimalMax(value = "180.0", message = "INVALID_LONGITUDE") Double longitude,
        @NotNull(message = "INVALID_INPUT_VALUE")
        @Min(value = 1, message = "INVALID_INPUT_VALUE")
        @Max(value = 3, message = "INVALID_INPUT_VALUE") Integer recommendationAttempt,
        @Size(max = 50, message = "INVALID_INPUT_VALUE")
        List<@NotBlank(message = "INVALID_INPUT_VALUE") String> excludeTrackIds) {
    // 최초 요청의 생략·null 제외 목록은 빈 목록으로 정규화한다.
    public CreateRecommendationPlaylistRequest {
        excludeTrackIds = excludeTrackIds == null ? List.of() : excludeTrackIds;
    }
}
