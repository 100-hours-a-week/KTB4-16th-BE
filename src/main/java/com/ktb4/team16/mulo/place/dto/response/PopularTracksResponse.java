package com.ktb4.team16.mulo.place.dto.response;

import com.ktb4.team16.mulo.place.dto.PopularTracksResponseDto;

public record PopularTracksResponse(
        String message,
        PopularTracksResponseDto data
) {
}
