package com.ktb4.team16.mulo.music.domain;

public record MusicSearchResult(
        MusicProvider provider,
        String externalTrackId,
        String title,
        String artistName,
        String albumImageUrl,
        String externalUrl
) {
}
