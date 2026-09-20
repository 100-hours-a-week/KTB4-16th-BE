package com.ktb4.team16.mulo.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MyPlacesQuery(
        @NotNull(message = "SW_LAT_REQUIRED")
        @DecimalMin(value = "-90", message = "INVALID_LATITUDE")
        @DecimalMax(value = "90", message = "INVALID_LATITUDE")
        BigDecimal swLat,

        @NotNull(message = "SW_LNG_REQUIRED")
        @DecimalMin(value = "-180", message = "INVALID_LONGITUDE")
        @DecimalMax(value = "180", message = "INVALID_LONGITUDE")
        BigDecimal swLng,

        @NotNull(message = "NE_LAT_REQUIRED")
        @DecimalMin(value = "-90", message = "INVALID_LATITUDE")
        @DecimalMax(value = "90", message = "INVALID_LATITUDE")
        BigDecimal neLat,

        @NotNull(message = "NE_LNG_REQUIRED")
        @DecimalMin(value = "-180", message = "INVALID_LONGITUDE")
        @DecimalMax(value = "180", message = "INVALID_LONGITUDE")
        BigDecimal neLng
) {
    public boolean hasValidBounds() {
        return swLat.compareTo(neLat) < 0 && swLng.compareTo(neLng) < 0;
    }
}
