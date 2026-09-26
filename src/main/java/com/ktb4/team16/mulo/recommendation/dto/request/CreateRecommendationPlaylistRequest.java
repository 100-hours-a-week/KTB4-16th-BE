package com.ktb4.team16.mulo.recommendation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateRecommendationPlaylistRequest(
        @NotNull(message = "LATITUDE_REQUIRED")
        @DecimalMin(value = "-90.0", message = "INVALID_LATITUDE")
        @DecimalMax(value = "90.0", message = "INVALID_LATITUDE") Double latitude,
        @NotNull(message = "LONGITUDE_REQUIRED")
        @DecimalMin(value = "-180.0", message = "INVALID_LONGITUDE")
        @DecimalMax(value = "180.0", message = "INVALID_LONGITUDE") Double longitude) {
}
