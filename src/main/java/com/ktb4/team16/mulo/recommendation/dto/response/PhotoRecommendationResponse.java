package com.ktb4.team16.mulo.recommendation.dto.response;

import java.util.List;

public record PhotoRecommendationResponse(String message, Long recommendationId, String recommendReason,
        List<Track> data) {
    public record Track(String externalTrackId, String title, String artistName,
            String albumImageUrl, String externalUrl) {
    }
}
