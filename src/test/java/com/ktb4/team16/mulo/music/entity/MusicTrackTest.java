package com.ktb4.team16.mulo.music.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MusicTrackTest {

    @Test
    void createsTrackWithAllMusicMetadata() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 25, 12, 0);

        MusicTrack track = MusicTrack.create(
                "external-id",
                "title",
                "artist",
                "album-image",
                "external-url",
                createdAt
        );

        assertThat(track.getExternalTrackId()).isEqualTo("external-id");
        assertThat(track.getTitle()).isEqualTo("title");
        assertThat(track.getArtistName()).isEqualTo("artist");
        assertThat(track.getAlbumImageUrl()).isEqualTo("album-image");
        assertThat(track.getExternalUrl()).isEqualTo("external-url");
        assertThat(track.getCreatedAt()).isEqualTo(createdAt);
        assertThat(track.getUpdatedAt()).isNull();
    }
}
