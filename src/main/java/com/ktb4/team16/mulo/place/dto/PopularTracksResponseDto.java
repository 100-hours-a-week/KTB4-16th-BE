package com.ktb4.team16.mulo.place.dto;

import java.util.List;

public record PopularTracksResponseDto(
            Long recordCount,
            List<Music> music
    ) {

    public record Music(
            Integer rank,
            Long musicTrackId,
            String title,
            String artistName,
            Long count
    ) {
    }
}
