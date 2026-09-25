package com.ktb4.team16.mulo.music.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "music_tracks")
@Getter
@NoArgsConstructor
public class MusicTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long musicTrackId;

    @Column(nullable = false, length = 22)
    private String externalTrackId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String artistName;

    @Column(nullable = false, length = 255)
    private String albumImageUrl;

    @Column(nullable = false, length = 255)
    private String externalUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private MusicTrack(
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl,
            LocalDateTime createdAt
    ) {
        this.externalTrackId = externalTrackId;
        this.title = title;
        this.artistName = artistName;
        this.albumImageUrl = albumImageUrl;
        this.externalUrl = externalUrl;
        this.createdAt = createdAt;
    }

    public static MusicTrack create(
            String externalTrackId,
            String title,
            String artistName,
            String albumImageUrl,
            String externalUrl,
            LocalDateTime createdAt
    ) {
        return new MusicTrack(
                externalTrackId,
                title,
                artistName,
                albumImageUrl,
                externalUrl,
                createdAt
        );
    }
}
