package com.ktb4.team16.mulo.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record RecommendationPlaylistResponse(String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long recommendationId,
        RecommendationPlaylistData data) {
}
